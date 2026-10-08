# Smart Ambulance 3-Device Hardware Setup Guide

## Architecture Overview

Your system now uses **3 separate devices**:

1. **Ambulance ESP32** (Transmitter) - LoRa + GPS + LED + Buzzer + Button
2. **ESP32 LoRa Receiver** - Receives LoRa, calculates GPS distance/bearing, sends
   UART commands, and mirrors every decision to Firebase so the dashboard and
   the driver app follow the real hardware
3. **Arduino Traffic Controller** - Controls traffic signals + RFID, receives UART commands
The receiver is the only device that talks to the cloud. The ambulance transmits
over LoRa only, and the traffic controller stays on its UART link, so neither
needs network credentials.

---

## Device 1: Ambulance ESP32 (Transmitter)

### Hardware Connections

**LoRa SX1278 (433MHz):**
- VCC → 3.3V
- GND → GND
- MISO → GPIO 19
- MOSI → GPIO 23
- SCK → GPIO 18
- NSS (CS) → GPIO 5
- RST → GPIO 14
- DIO0 → GPIO 2

**GPS Module (Neo-6M/M8N):**
- VCC → VIN/5V
- GND → GND
- TX → GPIO 16 (RX2)
- RX → GPIO 17 (TX2)

**OLED Display (SSD1306 128x64):**
- VCC → 3.3V
- GND → GND
- SDA → GPIO 21
- SCL → GPIO 22

**Status LED:**
- Long leg (+) → GPIO 25 (with 220Ω resistor)
- Short leg (-) → GND

**Active Buzzer:**
- (+) Pin → GPIO 26
- (-) Pin → GND

**OLED Display (SSD1306 128x64):**
- VCC → 3.3V
- GND → GND
- SDA → GPIO 21
- SCL → GPIO 22

**Emergency Button:**
- Pin A → GPIO 33
- Pin B → GND

**Power:**
- TP4056 Charger OUT+ → 3.3V
- TP4056 Charger OUT- → GND
- LiPo Battery → TP4056 input

### Firmware File
`firmware/ambulance_unit/ambulance_unit.ino`

---

## Device 2: ESP32 LoRa Receiver

### Hardware Connections

**LoRa SX1278 (433MHz):**
- VCC → 3.3V
- GND → GND
- MISO → GPIO 19
- MOSI → GPIO 23
- SCK → GPIO 18
- NSS (CS) → GPIO 5
- RST → GPIO 14
- DIO0 → GPIO 2

**UART to Arduino (Serial2):**
- TX2 (GPIO 17) → Arduino RX (Pin 0)
- RX2 (GPIO 16) → Arduino TX (Pin 1)
- GND → Arduino GND (common ground required)

**Power:**
- USB or external 5V supply

### Firmware File
`firmware/lora_receiver_esp32/lora_receiver_esp32.ino`

### UART Commands Sent to Arduino
- `AMBULANCE_APPROACH,<signal_id>` - Ambulance approaching, preempt specified signal (0-3)
- `AMBULANCE_OUT_OF_RANGE` - Ambulance out of range, restore normal traffic

The Arduino prints its own state on this same link, so the receiver also reads
it back. That is how an RFID stop-line release reaches the software: the release
happens on the Arduino, and the receiver turns it into a `rfid_clearance` event.

### Firebase Integration (what the software reads)

This unit is the bridge between the hardware and the software. It holds the
junction position and thresholds, triggers the local preemption, and mirrors
every decision into the Realtime Database so the dashboard and the driver app
show what the hardware is actually doing.

| Node | Write | Purpose |
| --- | --- | --- |
| `junctions/<junctionId>` | PATCH | publishes this unit's position and thresholds at boot, then its `signalState`, `preemptionMode`, `activeLane`, `activeAmbulanceId`, `distanceMeters`, `rssi`, `lastDwellTime` |
| `junctionEvents` | POST | one row per decision - `gps_preempt_started`, `rssi_preempt_started`, `rfid_clearance`, `normal_restored`, `timeout_restore`, `approach_tracking_expired` |
| `loraTelemetry/<junctionId>/<ambulanceId>` | PATCH | live position and approach telemetry |
| `ambulances/<ambulanceId>/lastLocation` | PATCH | position for the driver app's map |

Those four paths are the only ones the firmware writes, and the only ones open
without credentials in `database.rules.json`; everything else requires an
authenticated operator. Preemption still runs when WiFi is down - the cloud
layer is a mirror, not a dependency.

`updatedAt` and `timestamp` are epoch milliseconds, never `millis()`. The apps
derive data age from them, and a `millis()` value reads as 1970.

---

## Device 3: Arduino Traffic Controller

### Hardware Connections

**RFID Entry Reader (Reader 2):**
- SDA (SS) → Pin 8
- RST → Pin 7
- SCK → Pin 13
- MOSI → Pin 11
- MISO → Pin 12
- 3.3V → 3.3V
- GND → GND

**RFID Exit Reader (Reader 1):**
- SDA (SS) → Pin 10
- RST → Pin 9
- SCK → Pin 13 (shared)
- MOSI → Pin 11 (shared)
- MISO → Pin 12 (shared)
- 3.3V → 3.3V
- GND → GND

**Traffic Signal LEDs (using digital pin numbers):**

**Signal 0:**
- RED → D2
- GREEN → D3
- YELLOW → D17 (A3 on Arduino Uno)

**Signal 1:**
- RED → D4
- GREEN → D5
- YELLOW → D15 (A1 on Arduino Uno)

**Signal 2:**
- RED → D6
- GREEN → D16 (A2 on Arduino Uno)
- YELLOW → D14 (A0 on Arduino Uno)

**Signal 3:**
- RED → D18 (A4 on Arduino Uno)
- GREEN → D19 (A5 on Arduino Uno)
- (No yellow for signal 3)

**Note:** For Arduino Uno/Nano: A0=14, A1=15, A2=16, A3=17, A4=18, A5=19
For Arduino Mega: A0=54, A1=55, A2=56, A3=57, A4=58, A5=59

**UART from ESP32 (Serial2):**
- RX (Pin 0) → ESP32 TX2 (GPIO 17)
- TX (Pin 1) → ESP32 RX2 (GPIO 16)
- GND → ESP32 GND (common ground required)

**Power:**
- USB or external 5V supply

### Firmware File
`firmware/arduino_traffic_controller/arduino_traffic_controller.ino`

---

## UART Commands Received from ESP32

The Arduino receives these commands via Serial (9600 baud):

1. **`AMBULANCE_APPROACH,<signal_id>`**
   - ESP32 detects ambulance within 500m
   - Arduino preempts specified signal (0=NORTH, 1=EAST, 2=SOUTH, 3=WEST)
   - Signal stays green until ambulance exit

2. **`AMBULANCE_OUT_OF_RANGE`**
   - ESP32 detects ambulance out of range or packet timeout
   - Arduino restores normal traffic cycle

3. **`AMBULANCE_EXIT`**
   - Manual command (can be sent if needed)
   - Arduino restores normal traffic cycle

---

## System Operation Flow

### Normal Operation
1. Ambulance ESP32 broadcasts GPS data via LoRa every 1 second (when emergency active)
2. ESP32 LoRa Receiver receives LoRa packets
3. ESP32 calculates distance and bearing to junction
4. If ambulance within 500m, ESP32 sends `AMBULANCE_APPROACH,<signal_id>` to Arduino
5. Arduino preempts traffic signal (specified signal green, others red)
6. Ambulance crosses junction

### RFID Exit Detection
1. Ambulance crosses RFID exit reader
2. Arduino detects authorized RFID tag
3. Arduino restores normal traffic cycle
4. System returns to normal operation

### GPS Exit Detection
1. If ambulance moves out of range (>500m)
2. ESP32 sends `AMBULANCE_OUT_OF_RANGE` to Arduino
3. Arduino restores normal traffic cycle

### Manual RFID Entry (Legacy)
1. Ambulance crosses RFID entry reader
2. Arduino detects authorized RFID tag
3. Arduino preempts traffic signal (uses configured entrySignalID)
4. Ambulance crosses exit RFID to restore

---

## Configuration

### ESP32 LoRa Receiver Settings
Edit `firmware/lora_receiver_esp32/lora_receiver_esp32.ino`:

```cpp
// WiFi - needed for the Firebase mirror only
const char* WIFI_SSID = "your_wifi_ssid";
const char* WIFI_PASSWORD = "your_wifi_password";

// Firebase project host. No database secret: the four firmware paths are
// written without credentials, by design (see database.rules.json).
const char* FIREBASE_HOST = "smart-ambulance-36f9d-default-rtdb.firebaseio.com";

// Junction Configuration
struct JunctionConfig {
  String junctionId = "JNC001";
  String name = "Main Road Junction";      // display only, not published
  String approachLane = "Northbound";      // display only, not published
  double lat = 12.962;                     // MUST match the junction in the database
  double lng = 77.592;
  float approachThresholdMeters = 500.0;    // trigger distance in meters
  unsigned long gpsPacketTimeoutMs = 5000;
  unsigned long clearanceTimeoutMs = 90000;
  int rssiFallbackThresholdDbm = -65;
  int rssiConsecutivePacketCount = 3;
};
```

At boot the unit publishes its position and thresholds to
`junctions/<junctionId>`, so the database can never describe a different
junction from the one the hardware triggers on. Only the position and the
thresholds are written - `name`, `lane` and `readers` stay the dashboard's.

**`lat`/`lng` must match the junction the software displays.** If they are wrong
the computed distance is wrong, so preemption either never fires or fires at the
wrong place. You can fix it on the bench without reflashing - the receiver takes
these commands on its USB serial (115200 baud):

| Command | Effect |
| --- | --- |
| `STATUS` | print junction, threshold, WiFi/NTP state and current signal state |
| `SET_LAT <deg>` / `SET_LNG <deg>` | move the junction and republish it |
| `SET_THRESHOLD <m>` | change the trigger distance and republish it |
| `SET_JUNCTION <id>` | point the unit at another junction and republish |
| `RELOAD` | republish the junction config now |

**Do not put a database secret in the firmware.** Earlier revisions of this
guide told you to fetch one from Service Accounts → Database Secrets. That
secret has been removed from the code and the database no longer accepts it.

**To find the Firebase host:** Firebase Console → Project Settings → Your apps →
`databaseURL`. For this project it is
`smart-ambulance-36f9d-default-rtdb.firebaseio.com`.

### Arduino Traffic Controller Settings
Edit `firmware/arduino_traffic_controller/arduino_traffic_controller.ino`:

```cpp
String allowedUIDs[] = {
  "04C0F0F2021390"  // Add your authorized RFID tags
};

const int totalTags = 1;  // Update count if adding more tags

int entrySignalID = 0;  // Signal to preempt for manual RFID entry
```

### Ambulance ESP32 Settings
Edit `firmware/ambulance_unit/ambulance_unit.ino`:

```cpp
String AMBULANCE_ID = "AMB001";  // Your ambulance ID
String TRIP_ID = "TRIP001";      // Your trip ID
```

---

## Testing Procedure

### 1. Test Ambulance ESP32
- Upload `ambulance_unit.ino`
- Install required libraries:
  - LoRa by Sandeep Mistry
  - TinyGPSPlus by Mikal Hart
  - ArduinoJson by Benoit Blanchon
  - Adafruit SSD1306
  - Adafruit GFX Library
  - Wire (built-in)
- Open Serial Monitor (115200 baud)
- Send command: `EMERGENCY ON`
- Should see GPS data being broadcast via LoRa
- Test button toggles emergency mode
- Check LED and buzzer functionality
- Verify OLED display shows GPS coordinates and emergency status

### 2. Test ESP32 LoRa Receiver
- Upload `lora_receiver_esp32.ino`
- Install required libraries:
  - LoRa by Sandeep Mistry
  - ArduinoJson by Benoit Blanchon
- Set the WiFi credentials in the code
- Set `junction.lat` / `junction.lng` to the junction you are standing at
- Open Serial Monitor (115200 baud)
- Should see "WiFi connected!" with IP address, then
  "NTP time synced - cloud timestamps are epoch milliseconds."
- Should see "Junction config published: JNC001 at ..." - that position should
  match what the dashboard's junction panel shows
- Should see "LoRa Receiver Active. Listening for ambulance data..."
- Send `STATUS` to print the unit's view of the junction
- When the ambulance transmits, should see distance, bearing and RSSI per packet
- Should see UART commands being sent to Arduino
- On a preemption, should see `[EVENT] ... type=gps_preempt_started`, and that row
  should appear in the dashboard's event feed

### 3. Test Arduino Traffic Controller
- Upload `arduino_traffic_controller.ino`
- Open Serial Monitor (9600 baud)
- Should see "SYSTEM READY"
- Normal traffic cycle should run (signals cycle every 5 seconds)
- Test RFID entry/exit detection
- Test UART commands (manually send via Serial Monitor)

### 4. Integration Test
- Connect all 3 devices, and open the dashboard and the driver app
- Start ambulance emergency mode
- Watch ESP32 receiver detect ambulance
- Watch Arduino preempt traffic signal
- Watch the dashboard junction panel go to `priority_active` and the event feed
  show the preemption row
- Test RFID exit to restore normal traffic, and confirm the corridor log shows
  the stop-line release (`rfid_clearance`)
- Confirm the driver app's map shows the ambulance from `lastLocation`

### 5. Bench demo (ambulance + LoRa receiver + traffic signal unit)

If you are presenting with one ambulance sender, one LoRa receiver and one
`traffic_signal_unit.ino` board (the Arduino controller replaced by the signal
unit, which drives the lights from LoRa directly), note that the two receivers
are independent: the LoRa receiver feeds the dashboard, the traffic signal unit
drives the physical light. Both listen on 433 MHz, so one transmitter drives both.

The traffic signal unit ignores a packet unless the ambulance is inside 500 m of
its own junction **and** within 35 degrees of the bearing to that junction, so the
heading in your SIM is not optional. All three boards now default to the same
junction, 12.9620, 77.5920 (JNC001 in the database):

| Step | Where | Command / check |
| --- | --- | --- |
| 1 | LoRa receiver | Confirm Wi-Fi connected and `NTP time synced`. Send `STATUS` to print the junction it published |
| 2 | Traffic signal unit | Optional: send `SET_LAT 12.9620` and `SET_LNG 77.5920` if you moved it |
| 3 | Ambulance ESP32 | `SIM 12.9647,77.5920,180,40` - this sets a fix 300 m north of the junction and starts the emergency |
| 4 | Ambulance ESP32 | Expect `[TX] ...` every broadcast interval |
| 5 | LoRa receiver | Expect `AMBULANCE WITHIN TRIGGER ZONE!`, then `[EVENT] type=gps_preempt_started` |
| 6 | Traffic signal unit | Expect the ambulance direction to go green and the cross direction red |
| 7 | Dashboard | Junction panel goes to `priority_active`, event feed shows the preemption, map shows the ambulance |
| 8 | Either | Send `EMERGENCY OFF` on the ambulance, or stop transmitting, and confirm the corridor restores after the packet timeout |

The heading matters: 12.9647, 77.5920 sits north of the junction, so the bearing to
the junction is 180 degrees. A heading of 0 passes the LoRa receiver (it triggers on
distance alone) but the traffic signal unit rejects it as travelling away, and the
light will not change.

---

## Troubleshooting

### LoRa Not Working
- Check 433MHz frequency matches on both devices
- Verify antenna connections
- Check power supply (3.3V only)
- Ensure proper ground connections

### UART Communication Issues
- Verify TX/RX cross-connection (ESP32 TX2/GPIO 17 → Arduino RX/Pin 0)
- Verify RX/TX cross-connection (ESP32 RX2/GPIO 16 → Arduino TX/Pin 1)
- Ensure common ground between ESP32 and Arduino
- Check baud rate (9600 for Arduino, 115200 for ESP32 debug)
- Use Serial Monitor at correct baud rate
- Note: ESP32 uses Serial2 for Arduino communication (pins 16/17)

### RFID Not Working
- Check SPI pin connections
- Verify 3.3V power supply
- Check RFID tag UID matches allowedUIDs
- Test with Serial Monitor to see scanned UIDs

### Traffic Signals Not Working
- Verify LED pin connections
- Check current limiting resistors
- Test LEDs with simple digitalWrite in setup()
- Verify Arduino power supply

---

## Security Notes

1. **RFID Security**: Add authorized RFID tags to `allowedUIDs` array
2. **Ambulance Authorization**: the receiver currently acts on any well-formed
   LoRa packet, on the assumption that the ambulance unit is the only
   transmitter on the channel. Add an ID allowlist to the receiver before
   deploying it anywhere others can transmit on 433 MHz
3. **Physical Security**: Enclose hardware in tamper-proof enclosures
4. **Power Backup**: Consider battery backup for traffic controller

---

## Future Enhancements

1. ~~Add WiFi to ESP32 receiver for cloud integration (MQTT/Firebase)~~ - done:
   the receiver mirrors every decision to Firebase
2. Add multiple ambulance support
3. Add traffic flow sensors
4. Add emergency vehicle detection cameras
5. Add traffic analytics and reporting
