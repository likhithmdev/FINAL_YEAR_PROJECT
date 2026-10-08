/*
  ESP32 LoRa Receiver for Smart Ambulance System

  Receives GPS data from the ambulance via LoRa, decides whether a junction
  preemption is warranted, commands the Arduino traffic controller over UART,
  and mirrors the whole decision into Firebase so the dashboard and the driver
  app show what the hardware is actually doing.

  Hardware:
  - LoRa SX1278 (433MHz):
    * VCC: 3.3V, GND: GND, MISO: GPIO 19, MOSI: GPIO 23, SCK: GPIO 18
    * NSS (CS): GPIO 5, RST: GPIO 14, DIO0: GPIO 2
  - UART to Arduino (Serial2):
    * TX2 (GPIO 17): Connect to Arduino RX
    * RX2 (GPIO 16): Connect to Arduino TX
    * The Arduino prints its state on this same link, so this firmware also
      reads it back to learn about RFID releases it could not otherwise observe.
  - WiFi (built-in): required for the Firebase mirror; local preemption runs
    whether or not WiFi is up.

  Commands sent to the Arduino (unchanged - this is the hardware contract):
  - "AMBULANCE_APPROACH,<signal_id>" - preempt a signal (0=NORTH 1=EAST 2=SOUTH 3=WEST)
  - "AMBULANCE_OUT_OF_RANGE"         - ambulance gone, restore the normal cycle

  Software contract (what this unit writes to the Realtime Database)
  ------------------------------------------------------------------
  junctions/<junctionId>         PATCH  signalState, preemptionMode, activeLane,
                                        activeAmbulanceId, distanceMeters, rssi,
                                        lastDwellTime, updatedAt
  junctionEvents                 POST   one row per decision, shaped like the
                                        rows traffic_signal_unit.ino writes
  loraTelemetry/<junction>/<amb> PATCH  live position and approach telemetry
  ambulances/<amb>/lastLocation  PATCH  position for the driver app's map

  `updatedAt` and `timestamp` are epoch milliseconds, NOT millis(). The apps
  derive data age from those fields, and a millis() value reads as 1970. Every
  one of them is therefore written as Firebase's {".sv":"timestamp"} directive
  and stamped with the database's own server clock, which stays correct even
  when NTP is unreachable - common on campus and hotspot networks, where UDP 123
  is blocked while HTTPS is fine.

  Junction position and thresholds live in this unit and are PUBLISHED to
  junctions/<junctionId> at boot, so the database can never describe a
  different junction from the one the hardware triggers on. The dashboard and
  the driver app read the database; this unit is what keeps it honest.

  Event vocabulary: the Android app classifies an event by substring - "preempt"
  opens a corridor, "clearance" is a stop-line release, "restore"/"reset" is a
  hand-back. The dashboard also shows anything whose preemptionMode is non-null
  in the corridor log, so events that never opened a corridor send the field as
  null rather than "none" and stay out of it.

  Libraries:
  - LoRa by Sandeep Mistry
  - ArduinoJson by Benoit Blanchon (v6 API)
*/

#include <SPI.h>
#include <LoRa.h>
#include <ArduinoJson.h>
#include <math.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>

// ---------------------------------------------------------------------------
// Pin and link configuration
// ---------------------------------------------------------------------------

#define LORA_SS    5
#define LORA_RST   14
#define LORA_DIO0  2

// UART to Arduino (Hardware Serial 2)
// ESP32 TX2 (GPIO 17) -> Arduino RX
// ESP32 RX2 (GPIO 16) -> Arduino TX
#define ARDUINO_UART Serial2
#define ARDUINO_BAUD 9600
#define ARDUINO_TX_PIN 17
#define ARDUINO_RX_PIN 16

// WiFi Configuration
const char* WIFI_SSID = "Kamlesh";
const char* WIFI_PASSWORD = "12345678";

// Firebase Configuration
const char* FIREBASE_HOST = "smart-ambulance-36f9d-default-rtdb.firebaseio.com";

// ---------------------------------------------------------------------------
// Junction configuration
//
// These are the unit's own values and publishJunctionConfig() writes them into
// junctions/<junctionId> at boot. The position is the database's JNC001
// position on purpose: this sketch previously carried 13.013123, 77.629112, a
// pair roughly 60 km away from the junction the software displays.
//
// WARNING - the trigger geometry depends on this position. If the ambulance's
// GPS reads near 13.013123, 77.629112 then the computed distance will be tens
// of kilometres and preemption will never fire. Correct it on the bench with
// SET_LAT / SET_LNG on the USB serial rather than reflashing.
// ---------------------------------------------------------------------------

struct JunctionConfig {
  String junctionId = "JNC001";
  String name = "Main Road Junction";
  String approachLane = "Northbound";
  double lat = 12.962;
  double lng = 77.592;
  float approachThresholdMeters = 500.0;
  unsigned long gpsPacketTimeoutMs = 5000;
  unsigned long clearanceTimeoutMs = 90000;
  int rssiFallbackThresholdDbm = -65;
  int rssiConsecutivePacketCount = 3;
};

JunctionConfig junction;
bool configPublished = false;

const unsigned long TELEMETRY_INTERVAL_MS = 2000;

// ---------------------------------------------------------------------------
// Decision state
//
// Matches the state vocabulary the database uses for junctions/<id>.signalState
// so the dashboard renders the hardware's decision without translation.
// ---------------------------------------------------------------------------

enum SignalState {
  NORMAL,
  APPROACH_TRACKING,
  GPS_PREEMPT_ACTIVE,
  RSSI_PREEMPT_ACTIVE,
  RFID_CLEARED,
  TIMEOUT_RESTORE
};

SignalState state = NORMAL;

struct AmbulancePacket {
  String ambulanceId;
  String tripId;
  double lat = 0.0;
  double lng = 0.0;
  float speedKmph = 0.0;
  float headingDeg = 0.0;
  bool gpsFix = false;
  int rssi = -120;
  unsigned long receivedAt = 0;
};

AmbulancePacket activePacket;

String activeAmbulanceId = "";
String activeTripId = "";
unsigned long lastPacketAt = 0;
unsigned long preemptStartedAt = 0;
unsigned long clearedAt = 0;
unsigned long lastTelemetryAt = 0;
unsigned long lastWifiAttemptAt = 0;
int strongerRssiCount = 0;
int previousRssi = -120;
bool ambulanceInZone = false;
bool wifiConnected = false;
bool timeSynced = false;

// Forward declaration: used before its definition below.
void handleRfidClearance();

// ---------------------------------------------------------------------------
// Geo helpers
// ---------------------------------------------------------------------------

double toRadians(double degrees) { return degrees * PI / 180.0; }
double toDegrees(double radians) { return radians * 180.0 / PI; }

double normalizeDegrees(double degrees) {
  while (degrees < 0) degrees += 360.0;
  while (degrees >= 360.0) degrees -= 360.0;
  return degrees;
}

double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
  const double earthRadiusMeters = 6371000.0;
  double dLat = toRadians(lat2 - lat1);
  double dLng = toRadians(lng2 - lng1);
  double a = sin(dLat / 2) * sin(dLat / 2) +
             cos(toRadians(lat1)) * cos(toRadians(lat2)) *
             sin(dLng / 2) * sin(dLng / 2);
  return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a));
}

double bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
  double y = sin(toRadians(lng2 - lng1)) * cos(toRadians(lat2));
  double x = cos(toRadians(lat1)) * sin(toRadians(lat2)) -
             sin(toRadians(lat1)) * cos(toRadians(lat2)) * cos(toRadians(lng2 - lng1));
  return normalizeDegrees(toDegrees(atan2(y, x)));
}

// Ambulance -> junction. This is the direction the dashboard labels
// `bearingToJunctionDeg`, and the same convention traffic_signal_unit.ino uses,
// so both firmware paths agree. It used to be computed the other way round.
double bearingToJunction(const AmbulancePacket& packet) {
  return bearingDegrees(packet.lat, packet.lng, junction.lat, junction.lng);
}

// Junction -> ambulance: which arm of the junction the vehicle is on, which is
// what selects the signal to preempt.
double approachBearing(const AmbulancePacket& packet) {
  return bearingDegrees(junction.lat, junction.lng, packet.lat, packet.lng);
}

double currentDistanceMeters() {
  if (!activePacket.gpsFix) return 0.0;
  return haversineMeters(activePacket.lat, activePacket.lng, junction.lat, junction.lng);
}

// Determine which signal to preempt based on the approach arm
int getSignalFromAngle(float angle) {
  if (angle >= 315 || angle < 45) {
    return 0; // NORTH
  } else if (angle >= 45 && angle < 135) {
    return 1; // EAST
  } else if (angle >= 135 && angle < 225) {
    return 2; // SOUTH
  } else {
    return 3; // WEST
  }
}

void parseLaneDirection(float angle) {
  if (angle >= 315 || angle < 45) {
    Serial.println("SYSTEM DECISION: Clear NORTH Lane!");
  } else if (angle >= 45 && angle < 135) {
    Serial.println("SYSTEM DECISION: Clear EAST Lane!");
  } else if (angle >= 135 && angle < 225) {
    Serial.println("SYSTEM DECISION: Clear SOUTH Lane!");
  } else {
    Serial.println("SYSTEM DECISION: Clear WEST Lane!");
  }
}

// ---------------------------------------------------------------------------
// Cloud helpers
// ---------------------------------------------------------------------------

const char* signalStateName() {
  if (state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE) return "priority_active";
  if (state == TIMEOUT_RESTORE) return "timeout_restore";
  return "normal";
}

const char* preemptionModeName() {
  if (state == GPS_PREEMPT_ACTIVE) return "gps_lora";
  if (state == RSSI_PREEMPT_ACTIVE) return "rssi_fallback";
  return "none";
}

bool preemptionActive() {
  return state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE;
}

// Stamp a field with Firebase's own clock. The {".sv":"timestamp"} directive
// makes the database fill the value in on write, so what gets stored is an
// ordinary epoch-millisecond number - the same shape the firmware used to
// compute from NTP, and the dashboard and driver app need no changes. Unlike a
// local clock it is also authoritative, so the value can never read as 1970.
void setServerTimestamp(JsonObject parent, const char* key) {
  JsonObject stamp = parent.createNestedObject(key);
  stamp[".sv"] = "timestamp";
}

void syncTimeIfNeeded() {
  if (timeSynced || WiFi.status() != WL_CONNECTED) return;
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  if (time(nullptr) > 1700000000) {
    timeSynced = true;
    Serial.println("NTP time synced. Cloud timestamps come from Firebase's server clock.");
  }
}

void postFirebase(const String& path, const String& payload, const char* method) {
  if (WiFi.status() != WL_CONNECTED) return;

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  String url = String("https://") + FIREBASE_HOST + "/" + path + ".json";
  if (!http.begin(client, url)) return;
  http.setTimeout(2000);
  http.addHeader("Content-Type", "application/json");

  int code = (strcmp(method, "POST") == 0) ? http.POST(payload)
                                           : http.sendRequest("PATCH", payload);
  if (code <= 0) {
    Serial.print("Firebase write ");
    Serial.print(path);
    Serial.print(" error: ");
    Serial.println(http.errorToString(code));
  }
  http.end();
}

// Publish the junction facts this unit owns, so the database cannot describe a
// different junction from the one being triggered on. Only physical and
// behavioural values are written - operator-facing labels (name, lane, readers)
// stay the database's to set.
void publishJunctionConfig() {
  StaticJsonDocument<512> doc;
  doc["junctionId"] = junction.junctionId;
  JsonObject location = doc.createNestedObject("location");
  location["lat"] = junction.lat;
  location["lng"] = junction.lng;
  doc["approachThresholdMeters"] = junction.approachThresholdMeters;
  doc["gpsPacketTimeoutMs"] = junction.gpsPacketTimeoutMs;
  doc["clearanceTimeoutMs"] = junction.clearanceTimeoutMs;
  doc["rssiFallbackThresholdDbm"] = junction.rssiFallbackThresholdDbm;
  doc["rssiConsecutivePacketCount"] = junction.rssiConsecutivePacketCount;
  doc["reportedBy"] = "lora_receiver";
  setServerTimestamp(doc.as<JsonObject>(), "updatedAt");

  String payload;
  serializeJson(doc, payload);
  postFirebase(String("junctions/") + junction.junctionId, payload, "PATCH");

  configPublished = true;
  Serial.print("Junction config published: ");
  Serial.print(junction.junctionId);
  Serial.print(" at ");
  Serial.print(junction.lat, 6);
  Serial.print(", ");
  Serial.print(junction.lng, 6);
  Serial.print("  threshold ");
  Serial.print(junction.approachThresholdMeters, 0);
  Serial.println(" m");
}

String telemetryPayload() {
  double distance = currentDistanceMeters();
  StaticJsonDocument<512> doc;
  doc["junctionId"] = junction.junctionId;
  doc["ambulanceId"] = activePacket.ambulanceId;
  doc["tripId"] = activePacket.tripId;
  doc["lat"] = activePacket.lat;
  doc["lng"] = activePacket.lng;
  doc["speedKmph"] = activePacket.speedKmph;
  doc["headingDeg"] = activePacket.headingDeg;
  doc["gpsFix"] = activePacket.gpsFix;
  doc["rssi"] = activePacket.rssi;
  doc["distanceMeters"] = distance;
  doc["bearingToJunctionDeg"] = activePacket.gpsFix ? bearingToJunction(activePacket) : 0.0;
  doc["approachLane"] = junction.approachLane;
  doc["approaching"] = activePacket.gpsFix && distance <= junction.approachThresholdMeters;
  doc["preemptionEligible"] = preemptionActive();
  doc["source"] = state == RSSI_PREEMPT_ACTIVE ? "rssi_fallback" : "gps_lora";
  setServerTimestamp(doc.as<JsonObject>(), "timestamp");
  setServerTimestamp(doc.as<JsonObject>(), "updatedAt");

  String payload;
  serializeJson(doc, payload);
  return payload;
}

// The junction node as this unit believes it to be. Single source of truth for
// both the per-event update and the hand-back update.
String junctionStatePayload() {
  StaticJsonDocument<384> doc;
  doc["signalState"] = signalStateName();
  doc["preemptionMode"] = preemptionModeName();
  doc["activeLane"] = preemptionActive() ? junction.approachLane : "";
  doc["activeAmbulanceId"] = preemptionActive() ? activeAmbulanceId : "";
  doc["distanceMeters"] = currentDistanceMeters();
  doc["rssi"] = activePacket.rssi;
  if (state == RFID_CLEARED && preemptStartedAt > 0) {
    doc["lastDwellTime"] = String((clearedAt - preemptStartedAt) / 1000) + "s";
  }
  setServerTimestamp(doc.as<JsonObject>(), "updatedAt");

  String payload;
  serializeJson(doc, payload);
  return payload;
}

// `preemptionMode` is passed explicitly. A null value leaves the field out so
// the dashboard keeps events that never opened a corridor out of the corridor
// log; writing "none" there would put them in it.
String eventPayload(const char* eventType, const char* source, const char* preemptionMode) {
  StaticJsonDocument<512> doc;
  doc["ambulanceId"] = activeAmbulanceId;
  doc["tripId"] = activeTripId;
  doc["eventType"] = eventType;
  doc["junctionId"] = junction.junctionId;
  doc["junctionName"] = junction.name;
  doc["lane"] = junction.approachLane;
  if (preemptionMode != nullptr) doc["preemptionMode"] = preemptionMode;
  doc["distanceMeters"] = currentDistanceMeters();
  doc["rssi"] = activePacket.rssi;
  doc["source"] = source;
  setServerTimestamp(doc.as<JsonObject>(), "timestamp");

  String payload;
  serializeJson(doc, payload);
  return payload;
}

void publishTelemetry() {
  if (millis() - lastTelemetryAt < TELEMETRY_INTERVAL_MS) return;
  lastTelemetryAt = millis();

  postFirebase(String("loraTelemetry/") + junction.junctionId + "/" + activePacket.ambulanceId,
               telemetryPayload(), "PATCH");

  // Position for the driver app's map.
  StaticJsonDocument<192> locationDoc;
  locationDoc["lat"] = activePacket.lat;
  locationDoc["lng"] = activePacket.lng;
  locationDoc["source"] = "lora_receiver";
  setServerTimestamp(locationDoc.as<JsonObject>(), "updatedAt");
  String locationPayload;
  serializeJson(locationDoc, locationPayload);
  postFirebase(String("ambulances/") + activePacket.ambulanceId + "/lastLocation",
               locationPayload, "PATCH");
}

// One row per decision, plus the junction's current signal state, so the
// dashboard's event feed, corridor log and junction panel all follow the real
// hardware rather than only the two telemetry nodes.
void logEvent(const char* eventType, const char* source, const char* preemptionMode = nullptr) {
  Serial.print("[EVENT] junction=");
  Serial.print(junction.junctionId);
  Serial.print(" ambulance=");
  Serial.print(activeAmbulanceId);
  Serial.print(" type=");
  Serial.print(eventType);
  Serial.print(" source=");
  Serial.println(source);

  if (WiFi.status() != WL_CONNECTED) return;

  postFirebase("junctionEvents", eventPayload(eventType, source, preemptionMode), "POST");
  postFirebase(String("junctions/") + junction.junctionId, junctionStatePayload(), "PATCH");
}

// ---------------------------------------------------------------------------
// Arduino link
// ---------------------------------------------------------------------------

void sendToArduino(const char* command) {
  ARDUINO_UART.println(command);
  Serial.print("Sent to Arduino: ");
  Serial.println(command);
}

// The Arduino shares this UART with its own debug output, which we can read
// back. It is how an RFID stop-line release reaches us: that release happens on
// the Arduino, and without reading it here the event would never reach the app.
void pollArduinoUart() {
  static String line = "";
  while (ARDUINO_UART.available() > 0) {
    char c = (char)ARDUINO_UART.read();
    if (c == '\n' || c == '\r') {
      if (line.length() > 0) {
        if (line.indexOf("EXIT DETECTED (RFID)") >= 0) {
          handleRfidClearance();
        } else if (line.indexOf("UNAUTHORIZED RFID") >= 0) {
          Serial.println("Arduino reports an unauthorized tag at the stop line.");
        }
        line = "";
      }
    } else if (line.length() < 160) {
      line += c;
    } else {
      line = "";
    }
  }
}

// ---------------------------------------------------------------------------
// Decision logic
// ---------------------------------------------------------------------------

bool isApproachingByGps() {
  if (!activePacket.gpsFix) return false;
  return currentDistanceMeters() <= junction.approachThresholdMeters;
}

bool isRssiFallbackReady() {
  if (activePacket.gpsFix) {
    strongerRssiCount = 0;
    previousRssi = activePacket.rssi;
    return false;
  }

  if (activePacket.rssi >= previousRssi) {
    strongerRssiCount += 1;
  } else {
    strongerRssiCount = 0;
  }
  previousRssi = activePacket.rssi;

  return activePacket.rssi >= junction.rssiFallbackThresholdDbm &&
         strongerRssiCount >= junction.rssiConsecutivePacketCount;
}

void commandPreemption(int signalId) {
  char command[32];
  snprintf(command, sizeof(command), "AMBULANCE_APPROACH,%d", signalId);
  sendToArduino(command);
}

void startPreemption(SignalState nextState, const char* source, const char* mode) {
  state = nextState;
  activeAmbulanceId = activePacket.ambulanceId;
  activeTripId = activePacket.tripId;
  preemptStartedAt = millis();
  logEvent(nextState == GPS_PREEMPT_ACTIVE ? "gps_preempt_started" : "rssi_preempt_started",
           source, mode);
}

// Called when the Arduino reports the ambulance crossed the stop-line reader.
void handleRfidClearance() {
  if (!preemptionActive()) {
    Serial.println("RFID exit ignored: no preemption is active.");
    return;
  }
  state = RFID_CLEARED;
  clearedAt = millis();
  // logEvent also republishes the junction, while `state` still carries the
  // dwell time, so control is handed back in the same write.
  logEvent("rfid_clearance", "rc522_stop_line", "rfid_clearance");
  Serial.println("Stop-line release confirmed - corridor returned to normal control.");

  // The latch is deliberately left set: the vehicle is inside the trigger zone
  // until it drives out of range, and re-latching is what keeps the corridor
  // from being re-opened behind it.
  state = NORMAL;
  activeAmbulanceId = "";
  activeTripId = "";
  strongerRssiCount = 0;
}

void ingestPacket(const String& payload, int rssi) {
  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, payload);
  if (error) {
    Serial.print("JSON Parse Error: ");
    Serial.print(error.c_str());
    Serial.print("  raw: ");
    Serial.println(payload);
    return;
  }

  activePacket.ambulanceId = doc["ambulanceId"] | "";
  activePacket.tripId = doc["tripId"] | "";
  activePacket.lat = doc["lat"] | 0.0;
  activePacket.lng = doc["lng"] | 0.0;
  activePacket.speedKmph = doc["speedKmph"] | 0.0;
  activePacket.headingDeg = doc["headingDeg"] | 0.0;
  activePacket.gpsFix = doc["gpsFix"] | false;
  activePacket.rssi = rssi;
  activePacket.receivedAt = millis();

  lastPacketAt = millis();
  // While the trigger latch is held the vehicle is still inside the zone on its
  // way out, so this is not an approach: staying NORMAL keeps the tracking
  // timeout below from logging an "approach expired" row after a clean release.
  if (state == NORMAL && !ambulanceInZone) state = APPROACH_TRACKING;

  if (activePacket.gpsFix) {
    Serial.print("Ambulance "); Serial.print(activePacket.ambulanceId);
    Serial.print(" distance="); Serial.print(currentDistanceMeters(), 1);
    Serial.print(" m bearing="); Serial.print(approachBearing(activePacket), 1);
    Serial.print(" deg rssi="); Serial.println(activePacket.rssi);
  } else {
    Serial.print("Ambulance "); Serial.print(activePacket.ambulanceId);
    Serial.print(" reports no GPS fix. rssi="); Serial.println(activePacket.rssi);
  }

  // `ambulanceInZone` is latched when a corridor opens and released only once
  // the vehicle is confirmed out of range or its packets stop - never at the
  // stop-line release. The ambulance is still well inside the trigger distance
  // when it crosses that reader, so a distance-only test would preempt again on
  // the very next packet, re-command the Arduino and make the software log the
  // corridor opening and clearing over and over.
  if (!preemptionActive() && !ambulanceInZone) {
    if (isApproachingByGps()) {
      Serial.println("AMBULANCE WITHIN TRIGGER ZONE!");
      parseLaneDirection(approachBearing(activePacket));
      commandPreemption(getSignalFromAngle(approachBearing(activePacket)));
      startPreemption(GPS_PREEMPT_ACTIVE, "gps_lora", "gps_lora");
      ambulanceInZone = true;
    } else if (isRssiFallbackReady()) {
      Serial.println("GPS unhealthy but the signal is closing - RSSI fallback engaged.");
      commandPreemption(getSignalFromAngle(approachBearing(activePacket)));
      startPreemption(RSSI_PREEMPT_ACTIVE, "rssi_fallback", "rssi_fallback");
      ambulanceInZone = true;
    }
  }

  // Bookkeeping only. Whichever way the corridor was handed back - stop-line
  // release, clearance timeout or packet loss - has already logged its own
  // "restored" row, so logging one here as well would double the hand-back in
  // the apps' event feeds. The Arduino ignores this command once it has restored
  // normal traffic on its own.
  if (!preemptionActive() && ambulanceInZone && activePacket.gpsFix &&
      currentDistanceMeters() > junction.approachThresholdMeters) {
    Serial.println("Ambulance has left the trigger zone - trigger latch released.");
    ambulanceInZone = false;
    strongerRssiCount = 0;
    sendToArduino("AMBULANCE_OUT_OF_RANGE");
  }

  publishTelemetry();
}

void handleTimeouts() {
  unsigned long now = millis();

  // Tracking that never became a preemption. No corridor was opened, so no
  // preemptionMode: this stays out of the corridor log.
  if (state == APPROACH_TRACKING && now - lastPacketAt > junction.gpsPacketTimeoutMs) {
    state = NORMAL;
    strongerRssiCount = 0;
    Serial.println("Approach tracking expired - no packets.");
    logEvent("approach_tracking_expired", "local_timeout");
  }

  // Preemption running but the packets stopped: the vehicle is gone without
  // crossing the stop-line reader, so hand control back.
  if (preemptionActive() && now - lastPacketAt > junction.gpsPacketTimeoutMs) {
    sendToArduino("AMBULANCE_OUT_OF_RANGE");
    Serial.println("Packets stopped during preemption - restoring normal traffic.");
    state = NORMAL;
    logEvent("normal_restored", "packet_timeout");
    activeAmbulanceId = "";
    activeTripId = "";
    ambulanceInZone = false;
    strongerRssiCount = 0;
    // logEvent above already republished the junction in its restored state -
    // a second identical PATCH would just block the radio on another TLS
    // handshake while the ambulance is still transmitting.
    return;
  }

  // The corridor was already handed back at the stop line, so packet loss here
  // has nothing to restore - it only has to drop the latch so the next genuine
  // approach can preempt again.
  if (!preemptionActive() && state == NORMAL && ambulanceInZone &&
      now - lastPacketAt > junction.gpsPacketTimeoutMs) {
    Serial.println("Trigger latch released - the vehicle left without another packet.");
    ambulanceInZone = false;
    strongerRssiCount = 0;
  }

  // Safety valve: the corridor is held open for too long.
  if (preemptionActive() && now - preemptStartedAt > junction.clearanceTimeoutMs) {
    state = TIMEOUT_RESTORE;
    sendToArduino("AMBULANCE_OUT_OF_RANGE");
    Serial.println("Clearance timeout - restoring normal traffic.");
    logEvent("timeout_restore", "local_timeout");
    state = NORMAL;
    activeAmbulanceId = "";
    activeTripId = "";
    ambulanceInZone = false;
    strongerRssiCount = 0;
  }
}

// ---------------------------------------------------------------------------
// Connectivity
// ---------------------------------------------------------------------------

void handleWiFi() {
  if (WiFi.status() != WL_CONNECTED) {
    wifiConnected = false;
    if (millis() - lastWifiAttemptAt > 10000) {
      lastWifiAttemptAt = millis();
      Serial.println("Attempting to reconnect to WiFi...");
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
    return;
  }

  if (!wifiConnected) {
    wifiConnected = true;
    Serial.println("WiFi connected!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
  }
}

void handleCloud() {
  handleWiFi();
  if (!wifiConnected) return;

  syncTimeIfNeeded();
  if (!configPublished) {
    publishJunctionConfig();
  }
}

void printStatus() {
  Serial.println("--- receiver status ---");
  Serial.print("  junction: "); Serial.print(junction.junctionId);
  Serial.print("  "); Serial.println(junction.name);
  Serial.print("  position: "); Serial.print(junction.lat, 6);
  Serial.print(", "); Serial.println(junction.lng, 6);
  Serial.print("  threshold: "); Serial.print(junction.approachThresholdMeters, 0); Serial.println(" m");
  Serial.print("  config published to Firebase: "); Serial.println(configPublished ? "yes" : "no");
  Serial.print("  wifi: "); Serial.println(wifiConnected ? "connected" : "offline");
  Serial.print("  time synced: "); Serial.println(timeSynced ? "yes" : "no");
  Serial.print("  state: "); Serial.println(signalStateName());
  Serial.print("  ambulance in zone: "); Serial.println(ambulanceInZone ? "yes" : "no");
}

// publishJunctionConfig() runs from handleCloud() as soon as WiFi is up, so a
// bench correction reaches the database without a reflash. It deliberately does
// not wait on NTP: the timestamps it writes come from Firebase's server clock.
void printRepublishNote() {
  Serial.println("Junction config will be republished to Firebase.");
  if (wifiConnected) {
    publishJunctionConfig();
  }
}

void handleSerial() {
  if (!Serial.available()) return;

  String line = Serial.readStringUntil('\n');
  line.trim();
  if (line.length() == 0) return;

  if (line.equalsIgnoreCase("STATUS")) {
    printStatus();
    return;
  }

  if (line.equalsIgnoreCase("RELOAD")) {
    configPublished = false;
    publishJunctionConfig();
    return;
  }

  if (line.startsWith("SET_JUNCTION ")) {
    junction.junctionId = line.substring(13);
    configPublished = false;
    Serial.print("Junction ID set to ");
    Serial.println(junction.junctionId);
    printRepublishNote();
    return;
  }

  // Bench escape hatch: the trigger geometry depends on the junction position,
  // so it must be correctable without a reflash. Every one of these also
  // republishes, otherwise the database would keep describing the old geometry
  // while the unit triggers on the new one - the exact drift this design exists
  // to prevent.
  if (line.startsWith("SET_LAT ")) {
    junction.lat = line.substring(8).toDouble();
    Serial.print("Junction latitude set to ");
    Serial.println(junction.lat, 6);
    configPublished = false;
    printRepublishNote();
    return;
  }

  if (line.startsWith("SET_LNG ")) {
    junction.lng = line.substring(8).toDouble();
    Serial.print("Junction longitude set to ");
    Serial.println(junction.lng, 6);
    configPublished = false;
    printRepublishNote();
    return;
  }

  if (line.startsWith("SET_THRESHOLD ")) {
    junction.approachThresholdMeters = line.substring(14).toFloat();
    Serial.print("Trigger distance set to ");
    Serial.print(junction.approachThresholdMeters, 0);
    Serial.println(" m");
    configPublished = false;
    printRepublishNote();
    return;
  }

  Serial.println("Unknown command. Available: STATUS | RELOAD | SET_JUNCTION <id> | SET_LAT <deg> | SET_LNG <deg> | SET_THRESHOLD <m>");
}

// ---------------------------------------------------------------------------
// Setup / loop
// ---------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  while (!Serial) {}

  ARDUINO_UART.begin(ARDUINO_BAUD, SERIAL_8N1, ARDUINO_RX_PIN, ARDUINO_TX_PIN);

  Serial.println("--- ESP32 LoRa Receiver for Smart Ambulance ---");
  Serial.println("Initializing WiFi...");

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  int wifiAttempts = 0;
  while (WiFi.status() != WL_CONNECTED && wifiAttempts < 20) {
    delay(500);
    Serial.print(".");
    wifiAttempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    Serial.println(" connected!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
    syncTimeIfNeeded();
    publishJunctionConfig();
  } else {
    Serial.println(" failed. Local preemption still works; will retry later.");
  }

  Serial.println("Initializing LoRa Receiver Module...");
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa Receiver Init Failed!");
    while (1);
  }
  Serial.println("LoRa Receiver Active. Listening for ambulance data...");
  Serial.println("UART to Arduino ready at 9600 baud");
  Serial.println("Commands on USB serial: STATUS | RELOAD | SET_JUNCTION <id> | SET_LAT <deg> | SET_LNG <deg> | SET_THRESHOLD <m>");
}

void loop() {
  handleSerial();
  handleCloud();
  pollArduinoUart();

  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String packetData = "";
    while (LoRa.available()) {
      packetData += (char)LoRa.read();
    }
    ingestPacket(packetData, LoRa.packetRssi());
  }

  handleTimeouts();
}
