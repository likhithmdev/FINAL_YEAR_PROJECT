/*
  Smart Ambulance GPS-LoRa transmitter

  Broadcasts the same JSON the junction firmware already parses.
  Works with a Neo-6M GPS module, or with Serial simulation if GPS is not wired yet.

  Hardware Pin Configuration:
  - LoRa SX1278 (433MHz):
    * VCC: 3.3V
    * GND: GND
    * MISO: GPIO 19
    * MOSI: GPIO 23
    * SCK: GPIO 18
    * NSS (CS): GPIO 5
    * RST: GPIO 14
    * DIO0: GPIO 2
  - GPS Module (Neo-6M/M8N):
    * VCC: VIN/5V
    * GND: GND
    * TX: GPIO 16 (RX2)
    * RX: GPIO 17 (TX2)
  - OLED Display (SSD1306 128x64):
    * VCC: 3.3V
    * GND: GND
    * SDA: GPIO 21
    * SCL: GPIO 22
  - Status LED: GPIO 25 (with 220Ω resistor)
  - Active Buzzer: GPIO 26
  - Emergency Button: GPIO 33 (with internal pullup)
  - Power: TP4056 charger with LiPo battery

  Libraries to install in Arduino IDE:
  - LoRa by Sandeep Mistry
  - TinyGPSPlus by Mikal Hart
  - ArduinoJson by Benoit Blanchon
  - Adafruit SSD1306
  - Adafruit GFX Library
  - Wire (built-in)

  Serial commands (115200):
    EMERGENCY ON
    EMERGENCY OFF
    SIM 12.9647,77.5920,180,40
    GPS OFF
    GPS ON
    STATUS
    SET_AMB AMB002
    SET_TRIP TRIP002

  Bench demo
  ----------
  BENCH_DEMO_START (below) makes the board power up at a point 300 m north of
  JNC001 with the heading aimed at it, so an untethered board opens the corridor
  without anyone typing a SIM command. Override it live at any time with SIM.
*/

#include <Arduino.h>
#include <SPI.h>
#include <LoRa.h>
#include <TinyGPS++.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// Configurable ambulance and trip IDs (can be changed via Serial commands)
String AMBULANCE_ID = "AMB001";
String TRIP_ID = "TRIP001";

const int LORA_SS_PIN = 5;
const int LORA_RST_PIN = 14;
const int LORA_DIO0_PIN = 2;
const int EMERGENCY_BUTTON_PIN = 33;
const int GPS_RX_PIN = 16;
const int GPS_TX_PIN = 17;
const int STATUS_LED_PIN = 25;
const int BUZZER_PIN = 26;

// OLED Display Configuration
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define SCREEN_ADDRESS 0x3C
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

const unsigned long BROADCAST_INTERVAL_MS = 1000;
const bool START_IN_EMERGENCY = true;

TinyGPSPlus gps;
HardwareSerial gpsSerial(2);

bool emergencyActive = START_IN_EMERGENCY;
bool allowGpsFix = true;
bool loraReady = false;
bool usingSimulatedFix = false;
bool buttonDebounced = false;
unsigned long buttonDebounceTime = 0;

// Define to power up at the bench demo position: about 300 m north of JNC001
// (12.9620, 77.5920), which is inside the receivers' 500 m trigger, and with
// the heading aimed straight at the junction, which both roadside units
// require (each rejects a packet more than 35 degrees off the bearing).
// Comment this out to ship the realistic road position instead.
#define BENCH_DEMO_START

#ifdef BENCH_DEMO_START
double simLat = 12.9647;
double simLng = 77.5920;
float simHeadingDeg = 180.0;
float simSpeedKmph = 40.0;
#else
double simLat = 12.9750;
double simLng = 77.5946;
float simHeadingDeg = 185.0;
float simSpeedKmph = 42.0;
#endif

unsigned long lastBroadcastAt = 0;
bool lastButtonState = true;

String buildPacket(bool gpsFix, double lat, double lng, float speedKmph, float headingDeg) {
  StaticJsonDocument<256> doc;
  doc["ambulanceId"] = AMBULANCE_ID;
  doc["tripId"] = TRIP_ID;
  doc["lat"] = lat;
  doc["lng"] = lng;
  doc["speedKmph"] = speedKmph;
  doc["headingDeg"] = headingDeg;
  doc["gpsFix"] = gpsFix;
  doc["timestamp"] = millis();

  String payload;
  serializeJson(doc, payload);
  return payload;
}

void broadcastPacket() {
  if (!emergencyActive) return;

  bool gpsFix = false;
  double lat = simLat;
  double lng = simLng;
  float speedKmph = simSpeedKmph;
  float headingDeg = simHeadingDeg;

  if (allowGpsFix && gps.location.isValid()) {
    gpsFix = true;
    usingSimulatedFix = false;
    lat = gps.location.lat();
    lng = gps.location.lng();
    speedKmph = gps.speed.isValid() ? gps.speed.kmph() : 0.0;
    headingDeg = gps.course.isValid() ? gps.course.deg() : headingDeg;
  } else if (allowGpsFix && usingSimulatedFix) {
    gpsFix = true;
  }

  String payload = buildPacket(gpsFix, lat, lng, speedKmph, headingDeg);

  if (loraReady) {
    LoRa.beginPacket();
    LoRa.print(payload);
    LoRa.endPacket();
  }

  Serial.print("[TX] ");
  Serial.println(payload);
  
  // Update OLED display
  updateDisplay(lat, lng, speedKmph, headingDeg, gpsFix);
}

void printStatus() {
  Serial.print("emergency=");
  Serial.print(emergencyActive ? "ON" : "OFF");
  Serial.print(" gpsAllowed=");
  Serial.print(allowGpsFix ? "yes" : "no");
  Serial.print(" simFix=");
  Serial.print(usingSimulatedFix ? "yes" : "no");
  Serial.print(" lora=");
  Serial.println(loraReady ? "ready" : "offline");
}

void handleSerial() {
  if (!Serial.available()) return;

  String line = Serial.readStringUntil('\n');
  line.trim();
  if (line.length() == 0) return;

  if (line.equalsIgnoreCase("EMERGENCY ON")) {
    emergencyActive = true;
    Serial.println("Emergency broadcast started.");
    return;
  }

  if (line.equalsIgnoreCase("EMERGENCY OFF")) {
    emergencyActive = false;
    Serial.println("Emergency broadcast stopped.");
    return;
  }

  if (line.equalsIgnoreCase("GPS OFF")) {
    allowGpsFix = false;
    usingSimulatedFix = false;
    Serial.println("GPS marked unhealthy. Junction can use RSSI fallback.");
    return;
  }

  if (line.equalsIgnoreCase("GPS ON")) {
    allowGpsFix = true;
    Serial.println("GPS enabled.");
    return;
  }

  if (line.equalsIgnoreCase("STATUS")) {
    printStatus();
    return;
  }

  if (line.startsWith("SIM ")) {
    float lat = simLat;
    float lng = simLng;
    float heading = simHeadingDeg;
    float speed = simSpeedKmph;
    int parsed = sscanf(line.c_str(), "SIM %f,%f,%f,%f", &lat, &lng, &heading, &speed);
    if (parsed >= 2) {
      simLat = lat;
      simLng = lng;
      if (parsed >= 3) simHeadingDeg = heading;
      if (parsed >= 4) simSpeedKmph = speed;
      usingSimulatedFix = true;
      allowGpsFix = true;
      emergencyActive = true;
      Serial.println("Simulated GPS loaded and emergency started.");
      broadcastPacket();
    } else {
      Serial.println("Usage: SIM lat,lng,heading,speed");
    }
  }

  if (line.startsWith("SET_AMB ")) {
    AMBULANCE_ID = line.substring(8);
    Serial.print("Ambulance ID set to ");
    Serial.println(AMBULANCE_ID);
    return;
  }

  if (line.startsWith("SET_TRIP ")) {
    TRIP_ID = line.substring(9);
    Serial.print("Trip ID set to ");
    Serial.println(TRIP_ID);
    return;
  }
}

void handleEmergencyButton() {
  bool pressed = digitalRead(EMERGENCY_BUTTON_PIN) == LOW;
  if (pressed && !buttonDebounced && millis() - buttonDebounceTime > 50) {
    buttonDebounced = true;
    buttonDebounceTime = millis();
    emergencyActive = !emergencyActive;
    Serial.print("Button toggled emergency=");
    Serial.println(emergencyActive ? "ON" : "OFF");
    
    // Beep buzzer on state change
    digitalWrite(BUZZER_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
  }
  if (!pressed) {
    buttonDebounced = false;
  }
  lastButtonState = !pressed;
}

void updateIndicators() {
  // Status LED: ON when emergency active, OFF when normal
  digitalWrite(STATUS_LED_PIN, emergencyActive ? HIGH : LOW);
  
  // Buzzer: Short beep every 3 seconds when emergency active
  static unsigned long lastBuzzerAt = 0;
  if (emergencyActive && millis() - lastBuzzerAt > 3000) {
    lastBuzzerAt = millis();
    digitalWrite(BUZZER_PIN, HIGH);
    delay(50);
    digitalWrite(BUZZER_PIN, LOW);
  }
}

void updateDisplay(double lat, double lng, float speed, float heading, bool gpsFix) {
  display.clearDisplay();
  
  // Header line
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.print("AMB: ");
  display.print(AMBULANCE_ID);
  
  // Emergency status
  display.setCursor(90, 0);
  if (emergencyActive) {
    display.setTextColor(SSD1306_WHITE, SSD1306_BLACK); // Inverted text
    display.print("EMERGENCY");
    display.setTextColor(SSD1306_WHITE);
  } else {
    display.print("NORMAL");
  }
  
  // GPS coordinates
  display.setCursor(0, 12);
  display.print("LAT: ");
  display.println(lat, 6);
  display.setCursor(0, 22);
  display.print("LNG: ");
  display.println(lng, 6);
  
  // Speed and heading
  display.setCursor(0, 32);
  display.print("SPD: ");
  display.print(speed, 1);
  display.println(" km/h");
  display.setCursor(0, 42);
  display.print("HDG: ");
  display.print(heading, 1);
  display.println(" deg");
  
  // GPS fix status
  display.setCursor(0, 52);
  display.print("GPS: ");
  if (gpsFix) {
    display.println("FIXED");
  } else {
    display.println("NO FIX");
  }
  
  // LoRa status
  display.setCursor(70, 52);
  display.print("LoRa: ");
  display.println(loraReady ? "OK" : "FAIL");
  
  display.display();
}

void initDisplay() {
  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println("SSD1306 allocation failed");
    return;
  }
  
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Smart Ambulance");
  display.println("System Initializing...");
  display.display();
  delay(2000);
}

void setup() {
  Serial.begin(115200);
  pinMode(EMERGENCY_BUTTON_PIN, INPUT_PULLUP);
  pinMode(STATUS_LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  // Initialize I2C for OLED
  Wire.begin(21, 22);
  
  // Initialize OLED display
  initDisplay();

  LoRa.setPins(LORA_SS_PIN, LORA_RST_PIN, LORA_DIO0_PIN);
  loraReady = LoRa.begin(433E6);
  if (!loraReady) {
    Serial.println("LoRa start failed. Serial SIM still works for bench tests.");
  } else {
    Serial.println("LoRa transmitter ready.");
  }

  Serial.println("Ambulance GPS-LoRa unit ready.");
  Serial.println("Commands: EMERGENCY ON | EMERGENCY OFF | SIM lat,lng,heading,speed | GPS OFF | GPS ON | STATUS");
  printStatus();
  
  // Initial display update
  updateDisplay(simLat, simLng, simSpeedKmph, simHeadingDeg, false);
}

void loop() {
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  handleSerial();
  handleEmergencyButton();
  updateIndicators();

  unsigned long now = millis();
  if (now - lastBroadcastAt >= BROADCAST_INTERVAL_MS) {
    lastBroadcastAt = now;
    broadcastPacket();
  }
}
