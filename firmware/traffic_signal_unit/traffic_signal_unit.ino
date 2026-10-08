/*
  Smart Ambulance hybrid traffic preemption

  Local LoRa + RFID control always runs. MQTT/Firebase logging is optional.

  Libraries:
  - LoRa by Sandeep Mistry
  - MFRC522 by GithubCommunity
  - ArduinoJson by Benoit Blanchon
  - PubSubClient by Nick O'Leary

  Serial (115200):
    SIM {"ambulanceId":"AMB001","tripId":"TRIP001","lat":12.9647,"lng":77.5920,"speedKmph":40,"headingDeg":180,"gpsFix":true}
    SIMRSSI -60
    RFID RFID_TAG_001
    SET_JUNCTION JNC002
    SET_NAME Hospital Cross
    SET_LAT 12.9620
    SET_LNG 77.5920
    SET_LANE eastbound
    ADD_AMB AMB002
    SET_TAG RFID_TAG_002
    HELP
*/

#include <Arduino.h>
#include <SPI.h>
#include <LoRa.h>
#include <MFRC522.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <PubSubClient.h>
#include <time.h>

// Default junction configuration (can be changed via Serial commands)
String JUNCTION_ID = "JNC001";
String JUNCTION_NAME = "Main Road Junction";
String AUTHORIZED_AMBULANCE_ID = "AMB001";
String AUTHORIZED_RFID_TAG = "RFID_TAG_001";

// Position of junction JNC001. These are the database's own values for JNC001
// and they match the LoRa receiver's JunctionConfig, so the roadside unit and
// the cloud publish agree on where the junction is. The previous default,
// 12.9716, 77.5946, sat 1104 m from this point, which meant a packet close
// enough to light up the dashboard could never fire this unit's corridor and
// vice versa. Change them at runtime with SET_LAT / SET_LNG if needed.
double JUNCTION_LAT = 12.9620;
double JUNCTION_LNG = 77.5920;
String APPROACH_LANE = "northbound";

// Support for multiple authorized ambulances
const int MAX_AUTHORIZED_AMBULANCES = 5;
String AUTHORIZED_AMBULANCE_IDS[MAX_AUTHORIZED_AMBULANCES] = {"AMB001", "", "", "", ""};
int AUTHORIZED_AMBULANCE_COUNT = 1;

const float APPROACH_THRESHOLD_METERS = 500.0;
const float BEARING_TOLERANCE_DEG = 35.0;
const int RSSI_FALLBACK_THRESHOLD_DBM = -65;
const int RSSI_CONSECUTIVE_PACKET_COUNT = 3;
const unsigned long GPS_PACKET_TIMEOUT_MS = 5000;
const unsigned long CLEARANCE_TIMEOUT_MS = 90000;
const unsigned long POST_CLEARANCE_HOLD_MS = 3000;

const int LORA_SS_PIN = 5;
const int LORA_RST_PIN = 14;
const int LORA_DIO0_PIN = 26;
const int RFID_SS_PIN = 21;
const int RFID_RST_PIN = 22;
const int AMBULANCE_GREEN_PIN = 32;
const int AMBULANCE_RED_PIN = 33;
const int CROSS_GREEN_PIN = 25;
const int CROSS_RED_PIN = 27;

const char* WIFI_SSID = "";
const char* WIFI_PASSWORD = "";
const char* MQTT_BROKER = "broker.hivemq.com";
const int MQTT_PORT = 1883;
const char* FIREBASE_HOST = "smart-ambulance-36f9d-default-rtdb.firebaseio.com";

enum SignalState {
  NORMAL,
  APPROACH_TRACKING,
  GPS_PREEMPT_ACTIVE,
  RSSI_PREEMPT_ACTIVE,
  RFID_CLEARED,
  TIMEOUT_RESTORE
};

struct AmbulancePacket {
  String ambulanceId;
  String tripId;
  double lat = 0.0;
  double lng = 0.0;
  float speedKmph = 0.0;
  float headingDeg = 0.0;
  bool gpsFix = false;
  int rssi = -120;
  unsigned long timestamp = 0;
};

MFRC522 rfid(RFID_SS_PIN, RFID_RST_PIN);
WiFiClient mqttNet;
PubSubClient mqttClient(mqttNet);

SignalState state = NORMAL;
AmbulancePacket activePacket;
String activeAmbulanceId = "";
String activeTripId = "";
unsigned long lastPacketAt = 0;
unsigned long preemptStartedAt = 0;
unsigned long clearedAt = 0;
unsigned long lastWifiAttemptAt = 0;
unsigned long lastTelemetryCloudAt = 0;
int strongerRssiCount = 0;
int previousRssi = -120;
int simulatedRssi = -70;
bool cloudEnabled = false;
bool timeSynced = false;

int64_t cloudTimestamp();
void syncTimeIfNeeded();

double toRadians(double degrees) {
  return degrees * PI / 180.0;
}

double toDegrees(double radians) {
  return radians * 180.0 / PI;
}

double normalizeDegrees(double degrees) {
  while (degrees < 0) degrees += 360.0;
  while (degrees >= 360.0) degrees -= 360.0;
  return degrees;
}

double angularDifference(double a, double b) {
  double diff = fabs(normalizeDegrees(a) - normalizeDegrees(b));
  return diff > 180.0 ? 360.0 - diff : diff;
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

double currentDistanceMeters() {
  if (!activePacket.gpsFix) return 0.0;
  return haversineMeters(activePacket.lat, activePacket.lng, JUNCTION_LAT, JUNCTION_LNG);
}

void setNormalSignal() {
  digitalWrite(AMBULANCE_GREEN_PIN, LOW);
  digitalWrite(AMBULANCE_RED_PIN, HIGH);
  digitalWrite(CROSS_GREEN_PIN, HIGH);
  digitalWrite(CROSS_RED_PIN, LOW);
}

void setPrioritySignal() {
  digitalWrite(AMBULANCE_GREEN_PIN, HIGH);
  digitalWrite(AMBULANCE_RED_PIN, LOW);
  digitalWrite(CROSS_GREEN_PIN, LOW);
  digitalWrite(CROSS_RED_PIN, HIGH);
}

void publishMqtt(const char* topic, const String& payload) {
  if (!mqttClient.connected()) return;
  mqttClient.publish(topic, payload.c_str(), false);
}

void postFirebase(const String& path, const String& payload, const char* method) {
  if (WiFi.status() != WL_CONNECTED) return;

  WiFiClientSecure secureClient;
  secureClient.setInsecure();
  HTTPClient http;
  String url = String("https://") + FIREBASE_HOST + "/" + path + ".json";
  if (!http.begin(secureClient, url)) return;
  http.setTimeout(1500);
  http.addHeader("Content-Type", "application/json");
  if (strcmp(method, "POST") == 0) {
    http.POST(payload);
  } else {
    http.sendRequest("PATCH", payload);
  }
  http.end();
}

String eventPayload(const char* eventType, const char* source) {
  StaticJsonDocument<512> doc;
  doc["ambulanceId"] = activeAmbulanceId;
  doc["tripId"] = activeTripId;
  doc["eventType"] = eventType;
  doc["junctionId"] = JUNCTION_ID;
  doc["junctionName"] = JUNCTION_NAME;
  doc["lane"] = APPROACH_LANE;
  doc["rfidTagId"] = AUTHORIZED_RFID_TAG;
  doc["preemptionMode"] = strcmp(source, "rssi_fallback") == 0 ? "rssi_fallback" : preemptionModeName();
  doc["distanceMeters"] = currentDistanceMeters();
  doc["rssi"] = activePacket.rssi;
  doc["source"] = source;
  doc["timestamp"] = cloudTimestamp();

  String payload;
  serializeJson(doc, payload);
  return payload;
}

String signalPayload() {
  StaticJsonDocument<384> doc;
  doc["junctionId"] = JUNCTION_ID;
  doc["signalState"] = signalStateName();
  doc["activeLane"] = (state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE) ? APPROACH_LANE : "";
  doc["activeAmbulanceId"] = activeAmbulanceId;
  doc["preemptionMode"] = preemptionModeName();
  doc["distanceMeters"] = currentDistanceMeters();
  doc["rssi"] = activePacket.rssi;
  doc["updatedAt"] = cloudTimestamp();

  String payload;
  serializeJson(doc, payload);
  return payload;
}

String telemetryPayload() {
  double distance = currentDistanceMeters();
  double bearing = activePacket.gpsFix
      ? bearingDegrees(activePacket.lat, activePacket.lng, JUNCTION_LAT, JUNCTION_LNG)
      : 0.0;
  StaticJsonDocument<512> doc;
  doc["junctionId"] = JUNCTION_ID;
  doc["ambulanceId"] = activePacket.ambulanceId;
  doc["tripId"] = activePacket.tripId;
  doc["lat"] = activePacket.lat;
  doc["lng"] = activePacket.lng;
  doc["speedKmph"] = activePacket.speedKmph;
  doc["headingDeg"] = activePacket.headingDeg;
  doc["gpsFix"] = activePacket.gpsFix;
  doc["rssi"] = activePacket.rssi;
  doc["distanceMeters"] = distance;
  doc["bearingToJunctionDeg"] = bearing;
  doc["approachLane"] = APPROACH_LANE;
  doc["approaching"] = activePacket.gpsFix && distance <= APPROACH_THRESHOLD_METERS;
  doc["preemptionEligible"] = state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE;
  doc["updatedAt"] = cloudTimestamp();

  String payload;
  serializeJson(doc, payload);
  return payload;
}

void publishTelemetry() {
  if (!cloudEnabled || WiFi.status() != WL_CONNECTED) return;
  if (millis() - lastTelemetryCloudAt < 2000) return;
  lastTelemetryCloudAt = millis();

  String payload = telemetryPayload();
  String approachTopic = String("smart-ambulance/junctions/") + JUNCTION_ID + "/approach";
  String gpsTopic = String("smart-ambulance/ambulances/") + AUTHORIZED_AMBULANCE_ID + "/lora-gps";
  publishMqtt(approachTopic.c_str(), payload);
  publishMqtt(gpsTopic.c_str(), payload);
  postFirebase(String("loraTelemetry/") + JUNCTION_ID + "/" + AUTHORIZED_AMBULANCE_ID, payload, "PATCH");
}

void logEvent(const char* eventType, const char* source) {
  Serial.print("[EVENT] junction=");
  Serial.print(JUNCTION_ID);
  Serial.print(" ambulance=");
  Serial.print(activeAmbulanceId);
  Serial.print(" trip=");
  Serial.print(activeTripId);
  Serial.print(" type=");
  Serial.print(eventType);
  Serial.print(" source=");
  Serial.println(source);

  if (!cloudEnabled || WiFi.status() != WL_CONNECTED) return;

  String events = eventPayload(eventType, source);
  String signal = signalPayload();
  String eventsTopic = String("smart-ambulance/junctions/") + JUNCTION_ID + "/events";
  String signalTopic = String("smart-ambulance/junctions/") + JUNCTION_ID + "/signal";
  publishMqtt(eventsTopic.c_str(), events);
  publishMqtt(signalTopic.c_str(), signal);
  postFirebase("junctionEvents", events, "POST");

  StaticJsonDocument<384> junction;
  bool priority = state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE;
  junction["signalState"] = signalStateName();
  junction["preemptionMode"] = preemptionModeName();
  junction["activeLane"] = APPROACH_LANE;
  junction["activeAmbulanceId"] = priority ? activeAmbulanceId : "";
  junction["distanceMeters"] = currentDistanceMeters();
  junction["rssi"] = activePacket.rssi;
  if (state == RFID_CLEARED && preemptStartedAt > 0) {
    junction["lastDwellTime"] = String((clearedAt - preemptStartedAt) / 1000) + "s";
  }
  junction["updatedAt"] = cloudTimestamp();
  String junctionPayload;
  serializeJson(junction, junctionPayload);
  postFirebase(String("junctions/") + JUNCTION_ID, junctionPayload, "PATCH");
}

void ensureMqtt() {
  if (!cloudEnabled || WiFi.status() != WL_CONNECTED || mqttClient.connected()) return;
  String clientId = String("jnc-") + JUNCTION_ID + "-" + String((uint32_t)ESP.getEfuseMac(), HEX);
  mqttClient.connect(clientId.c_str());
}

int64_t cloudTimestamp() {
  time_t now = time(nullptr);
  if (now > 1700000000) return (int64_t)now * 1000;
  return millis();
}

void syncTimeIfNeeded() {
  if (timeSynced || WiFi.status() != WL_CONNECTED) return;
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  if (time(nullptr) > 1700000000) {
    timeSynced = true;
    Serial.println("NTP time synced for cloud timestamps.");
  }
}

void handleCloud() {
  if (!cloudEnabled) return;

  if (WiFi.status() != WL_CONNECTED && millis() - lastWifiAttemptAt > 10000) {
    lastWifiAttemptAt = millis();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }

  syncTimeIfNeeded();
  ensureMqtt();
  mqttClient.loop();
}

bool isAuthorizedAmbulance(const String& ambulanceId) {
  for (int i = 0; i < AUTHORIZED_AMBULANCE_COUNT; i++) {
    if (AUTHORIZED_AMBULANCE_IDS[i] == ambulanceId) {
      return true;
    }
  }
  return false;
}

bool parseLoRaPacket(const String& payload, int rssi, AmbulancePacket& packet) {
  StaticJsonDocument<384> doc;
  DeserializationError error = deserializeJson(doc, payload);
  if (error) {
    Serial.print("Invalid LoRa JSON: ");
    Serial.println(error.c_str());
    return false;
  }

  packet.ambulanceId = doc["ambulanceId"] | "";
  packet.tripId = doc["tripId"] | "";
  packet.lat = doc["lat"] | 0.0;
  packet.lng = doc["lng"] | 0.0;
  packet.speedKmph = doc["speedKmph"] | 0.0;
  packet.headingDeg = doc["headingDeg"] | 0.0;
  packet.gpsFix = doc["gpsFix"] | false;
  packet.rssi = rssi;
  packet.timestamp = millis();

  return isAuthorizedAmbulance(packet.ambulanceId);
}

bool isApproachingByGps(const AmbulancePacket& packet) {
  if (!packet.gpsFix) return false;

  double distance = haversineMeters(packet.lat, packet.lng, JUNCTION_LAT, JUNCTION_LNG);
  double bearing = bearingDegrees(packet.lat, packet.lng, JUNCTION_LAT, JUNCTION_LNG);
  double headingError = angularDifference(packet.headingDeg, bearing);

  Serial.print("distanceMeters=");
  Serial.print(distance);
  Serial.print(" bearing=");
  Serial.print(bearing);
  Serial.print(" headingError=");
  Serial.println(headingError);

  return distance <= APPROACH_THRESHOLD_METERS && headingError <= BEARING_TOLERANCE_DEG;
}

bool isRssiFallbackReady(const AmbulancePacket& packet) {
  if (packet.gpsFix) {
    strongerRssiCount = 0;
    previousRssi = packet.rssi;
    return false;
  }

  if (packet.rssi >= previousRssi) {
    strongerRssiCount += 1;
  } else {
    strongerRssiCount = 0;
  }

  previousRssi = packet.rssi;

  return packet.rssi >= RSSI_FALLBACK_THRESHOLD_DBM &&
         strongerRssiCount >= RSSI_CONSECUTIVE_PACKET_COUNT;
}

void startPreemption(SignalState nextState, const char* source) {
  state = nextState;
  activeAmbulanceId = activePacket.ambulanceId;
  activeTripId = activePacket.tripId;
  preemptStartedAt = millis();
  setPrioritySignal();
  logEvent(nextState == GPS_PREEMPT_ACTIVE ? "gps_preempt_started" : "rssi_preempt_started", source);
}

void ingestPacket(const String& payload, int rssi) {
  AmbulancePacket packet;
  if (!parseLoRaPacket(payload, rssi, packet)) {
    Serial.println("LoRa packet ignored: unauthorized ambulance");
    return;
  }

  activePacket = packet;
  lastPacketAt = millis();
  if (state == NORMAL) state = APPROACH_TRACKING;

  if (state == APPROACH_TRACKING && isApproachingByGps(activePacket)) {
    startPreemption(GPS_PREEMPT_ACTIVE, "gps_lora");
  } else if (state == APPROACH_TRACKING && isRssiFallbackReady(activePacket)) {
    startPreemption(RSSI_PREEMPT_ACTIVE, "rssi_fallback");
  }

  publishTelemetry();
}

String readRfidTag() {
  if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) {
    return "";
  }

  String uid = "";
  for (byte i = 0; i < rfid.uid.size; i++) {
    if (rfid.uid.uidByte[i] < 0x10) uid += "0";
    uid += String(rfid.uid.uidByte[i], HEX);
  }
  uid.toUpperCase();

  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();
  return uid;
}

void applyRfidTag(const String& tag) {
  Serial.print("RFID UID: ");
  Serial.println(tag);

  if (state != GPS_PREEMPT_ACTIVE && state != RSSI_PREEMPT_ACTIVE) {
    Serial.println("RFID ignored for restore: preemption is not active. UID printed for mapping.");
    return;
  }

  if (tag == AUTHORIZED_RFID_TAG) {
    state = RFID_CLEARED;
    clearedAt = millis();
    logEvent("rfid_clearance", "rc522_stop_line");
  } else {
    logEvent("invalid_rfid_tag", "rc522_stop_line");
    Serial.println("Copy the UID above into AUTHORIZED_RFID_TAG if this is the ambulance tag.");
  }
}

void handleLoRa() {
  int packetSize = LoRa.parsePacket();
  if (!packetSize) return;

  String payload = "";
  while (LoRa.available()) {
    payload += (char)LoRa.read();
  }

  ingestPacket(payload, LoRa.packetRssi());
}

void handleRfidClearance() {
  String tag = readRfidTag();
  if (tag.length() == 0) return;
  applyRfidTag(tag);
}

void handleTimeouts() {
  unsigned long now = millis();

  if (state == APPROACH_TRACKING && now - lastPacketAt > GPS_PACKET_TIMEOUT_MS) {
    state = NORMAL;
    strongerRssiCount = 0;
    setNormalSignal();
    logEvent("approach_tracking_expired", "local_timeout");
  }

  if ((state == GPS_PREEMPT_ACTIVE || state == RSSI_PREEMPT_ACTIVE) &&
      now - preemptStartedAt > CLEARANCE_TIMEOUT_MS) {
    state = TIMEOUT_RESTORE;
    setNormalSignal();
    logEvent("timeout_restore", "local_timeout");
  }

  if (state == RFID_CLEARED && now - clearedAt > POST_CLEARANCE_HOLD_MS) {
    state = NORMAL;
    strongerRssiCount = 0;
    setNormalSignal();
    logEvent("normal_restored", "rfid_clearance");
  }

  if (state == TIMEOUT_RESTORE) {
    state = NORMAL;
  }
}

void printHelp() {
  Serial.println("Commands:");
  Serial.println("  SIM {json}              inject a GPS-LoRa packet");
  Serial.println("  SIMRSSI -60             set simulated RSSI for the next SIM packet");
  Serial.println("  RFID TAG                inject a stop-line tag, e.g. RFID RFID_TAG_001");
  Serial.println("  SET_JUNCTION JNC002      set junction ID");
  Serial.println("  SET_NAME Hospital Cross  set junction name");
  Serial.println("  SET_LAT 12.9750         set junction latitude");
  Serial.println("  SET_LNG 77.5946         set junction longitude");
  Serial.println("  SET_LANE eastbound      set approach lane");
  Serial.println("  ADD_AMB AMB002           add authorized ambulance ID");
  Serial.println("  SET_TAG RFID_TAG_002     set authorized RFID tag");
  Serial.println("  HELP");
}

void handleSerial() {
  if (!Serial.available()) return;

  String line = Serial.readStringUntil('\n');
  line.trim();
  if (line.length() == 0) return;

  if (line.equalsIgnoreCase("HELP")) {
    printHelp();
    return;
  }

  if (line.startsWith("SIMRSSI ")) {
    simulatedRssi = line.substring(8).toInt();
    Serial.print("Simulated RSSI set to ");
    Serial.println(simulatedRssi);
    return;
  }

  if (line.startsWith("RFID ")) {
    applyRfidTag(line.substring(5));
    return;
  }

  if (line.startsWith("SIM ")) {
    ingestPacket(line.substring(4), simulatedRssi);
    return;
  }

  if (line.startsWith("{")) {
    ingestPacket(line, simulatedRssi);
    return;
  }

  if (line.startsWith("SET_JUNCTION ")) {
    JUNCTION_ID = line.substring(13);
    Serial.print("Junction ID set to ");
    Serial.println(JUNCTION_ID);
    return;
  }

  if (line.startsWith("SET_NAME ")) {
    JUNCTION_NAME = line.substring(9);
    Serial.print("Junction name set to ");
    Serial.println(JUNCTION_NAME);
    return;
  }

  if (line.startsWith("SET_LAT ")) {
    JUNCTION_LAT = line.substring(8).toDouble();
    Serial.print("Junction latitude set to ");
    Serial.println(JUNCTION_LAT, 6);
    return;
  }

  if (line.startsWith("SET_LNG ")) {
    JUNCTION_LNG = line.substring(8).toDouble();
    Serial.print("Junction longitude set to ");
    Serial.println(JUNCTION_LNG, 6);
    return;
  }

  if (line.startsWith("SET_LANE ")) {
    APPROACH_LANE = line.substring(9);
    Serial.print("Approach lane set to ");
    Serial.println(APPROACH_LANE);
    return;
  }

  if (line.startsWith("ADD_AMB ")) {
    String newAmbulanceId = line.substring(8);
    if (AUTHORIZED_AMBULANCE_COUNT < MAX_AUTHORIZED_AMBULANCES) {
      AUTHORIZED_AMBULANCE_IDS[AUTHORIZED_AMBULANCE_COUNT] = newAmbulanceId;
      AUTHORIZED_AMBULANCE_COUNT++;
      Serial.print("Added authorized ambulance: ");
      Serial.println(newAmbulanceId);
    } else {
      Serial.println("Maximum authorized ambulances reached.");
    }
    return;
  }

  if (line.startsWith("SET_TAG ")) {
    AUTHORIZED_RFID_TAG = line.substring(8);
    Serial.print("Authorized RFID tag set to ");
    Serial.println(AUTHORIZED_RFID_TAG);
    return;
  }

  Serial.println("Unknown command. Type HELP.");
}

void setupPins() {
  pinMode(AMBULANCE_GREEN_PIN, OUTPUT);
  pinMode(AMBULANCE_RED_PIN, OUTPUT);
  pinMode(CROSS_GREEN_PIN, OUTPUT);
  pinMode(CROSS_RED_PIN, OUTPUT);
  setNormalSignal();
}

void setup() {
  Serial.begin(115200);
  while (!Serial) {}

  setupPins();

  SPI.begin();
  rfid.PCD_Init();

  LoRa.setPins(LORA_SS_PIN, LORA_RST_PIN, LORA_DIO0_PIN);
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa start failed. Serial SIM still works.");
  } else {
    Serial.println("LoRa receiver ready.");
  }

  cloudEnabled = strlen(WIFI_SSID) > 0;
  if (cloudEnabled) {
    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setBufferSize(1024);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    lastWifiAttemptAt = millis();
    Serial.println("Cloud logging enabled. Junction still works if Wi-Fi is down.");
  } else {
    Serial.println("Cloud logging off. Set WIFI_SSID to enable MQTT/Firebase.");
  }

  Serial.println("Hybrid preemption controller ready.");
  printHelp();
}

void loop() {
  handleSerial();
  handleLoRa();
  handleRfidClearance();
  handleTimeouts();
  handleCloud();
}
