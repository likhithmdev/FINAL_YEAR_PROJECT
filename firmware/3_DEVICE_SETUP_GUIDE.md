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

The receiver does not compute those values from its own clock. It writes them as
Firebase's `{".sv":"timestamp"}` directive and lets the database stamp them with
its server clock. What lands in the database is an ordinary epoch-millisecond
number, identical in shape to before, but it is authoritative and it stays
correct when NTP is unreachable - which is common, because campus and phone
hotspot networks routinely block UDP 123 while HTTPS works fine. NTP is still
attempted, and `STATUS` reports it, but nothing depends on it.

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

**Traffic Signal LEDs:**

| Signal | RED | GREEN | YELLOW |
| --- | --- | --- | --- |
| 0 | D2 | D3 | A3 |
| 1 | D4 | D5 | A4 |
| 2 | D6 | A1 | A5 |
| 3 | A0 | A2 | (none) |

**Note:** On an Uno/Nano, A0-A5 *are* digital pins 14-19, so `A3` and `17` name
the same pin; the sketch uses the `A0`-`A5` names because that is how the board
is silkscreened (on a Mega they are 54-59). Signal 3 has no yellow head, which
is why `yellowLED[]` holds only three entries and the cycle step is guarded with
`previousSignal < 3`.

**UART from ESP32 (Serial2):**
- RX (Pin 0) → ESP32 TX2 (GPIO 17)
- TX (Pin 1) → ESP32 RX2 (GPIO 16)
- GND → ESP32 GND (common ground required)

> **Upload gotcha:** the ESP32's TX pin idles high on the Uno's RX line, which
> is the same line the USB bridge drives. That contention causes intermittent
> `stk500_getsync(): not in sync: resp=0x00` upload failures - if an upload
> fails, retry, and if it keeps failing unplug the ESP32 → Uno RX wire or power
> the roadside ESP32 down.
>
> **Voltage:** the Uno's TX (pin 1) swings to 5 V and the ESP32's GPIO 16 is not
> 5 V tolerant. Put a divider (roughly 1 kΩ over 2 kΩ) on the Uno TX → ESP32 RX
> line so the receiver's pin is not over-driven.

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

### Replies the Arduino sends back

The line is not one-way. An RFID stop-line release happens on the Arduino, so
the receiver reads the Arduino's serial output back and turns those lines into
`rfid_clearance` database events. It matches them by substring, so the wording
below is load-bearing: renaming one silently disables the handshake:

| Arduino prints | Receiver reaction |
| --- | --- |
| `AMBULANCE EXIT DETECTED (RFID)` | publishes an `rfid_clearance` event and restores the corridor |
| `UNAUTHORIZED RFID tag` | logs an unauthorized tag at the stop line |

`node scripts/check-uart-contract.mjs` asserts this contract in both directions
(commands, replies, baud rate, and the fixed `substring()` offset used to read
the signal ID) so a rename on either side fails the build instead of quietly
breaking the junction.

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
  double lat = 13.013123;                  // MUST match the junction the other units use
  double lng = 77.629112;
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

**The junction position is baked into four source files.** They have to agree,
otherwise the corridor the dashboard draws and the area the hardware triggers on
describe different places:

| Where | What holds the position |
| --- | --- |
| `firmware/lora_receiver_esp32/lora_receiver_esp32.ino` | `JunctionConfig.lat` / `.lng` |
| `firmware/traffic_signal_unit/traffic_signal_unit.ino` | `JUNCTION_LAT` / `JUNCTION_LNG` |
| `firmware/ambulance_unit/ambulance_unit.ino` | the `BENCH_DEMO_START` position, 300 m north of the junction |
| `dashboard/src/lib/model.js`, `seed.js`, `demoScenario.js` | the `JNC001` fallback, the seed row and the demo route |

The `junctions/JNC001` row is not a fifth copy to maintain: the receiver
republishes it from its own config at boot, and on demand with `RELOAD`. An
earlier revision had the firmware on 12.9620, 77.5920 and the dashboard on
12.9716, 77.5946 - about 1.1 km apart - which is exactly wide enough to make the
two disagree in a live demo.

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

#### Bench demo start position

The same file carries this switch, near the simulated-fix variables:

```cpp
#define BENCH_DEMO_START
```

With it defined, the board powers up at **13.01582, 77.629112, heading 180,
40 km/h** - which measures 300 m north of JNC001, so the packet is inside the
receivers' 500 m trigger and the heading matches the bearing to the junction
exactly. A board on a battery therefore opens the corridor with nobody typing a
command.

Comment the line out to restore the realistic road default (12.9750, 77.5946,
heading 185), which sits about 5.7 km from JNC001 and triggers nothing. The board also
starts transmitting immediately (`START_IN_EMERGENCY`), so `EMERGENCY ON` is not
needed either; use the `SIM` command only to move it somewhere else at runtime.

---

## Flashing the Boards

Firmware is flashed with `arduino-cli`, one board at a time. A helper script
wraps the whole thing:

```bash
bash scripts/flash-firmware.sh list                  # find the board's COM port
bash scripts/flash-firmware.sh compile all           # pre-flight, no board needed
bash scripts/flash-firmware.sh flash receiver auto   # wait for a new port, then flash
bash scripts/flash-firmware.sh monitor receiver COM9 # watch it boot
```

Board aliases: `receiver`, `signal`, `ambulance`, `controller`.

### Why one board at a time

Cheap ESP32 boards usually carry a CH340 or CP2102 USB bridge, and clones often
ship with no unique serial number - so two identical boards appear as two
indistinguishable "USB-SERIAL CH340" ports. A typical Windows machine also has
several Bluetooth virtual serial ports that always exist. Connecting one board
at a time removes the ambiguity: the port that *appears* is the board you just
plugged in.

Passing `auto` instead of a COM number uses that fact directly: it snapshots the
ports, waits for one that was not there before, and flashes to it. That is the
simplest flow on a machine with Bluetooth ports. Set `SAPTCS_PORT_WAIT` to change
the wait (default 120s). If a board is already plugged in, `auto` will not see a
new port - pass the COM number explicitly instead.

The order that works best: **receiver -> signal -> ambulance**. Each board has a
distinct thing to check right after flashing it, while it is still tethered.

### Manual equivalent

```bash
arduino-cli compile -b esp32:esp32:esp32 firmware/lora_receiver_esp32
arduino-cli upload  -b esp32:esp32:esp32 -p COM9 firmware/lora_receiver_esp32
```

The Uno controller uses `-b arduino:avr:uno`.

### If an upload fails

- **Port busy** - close any open Serial Monitor first; the upload needs
exclusive access to the port.
- **No port appears** - install the USB-serial driver (CP210x for Silicon Labs,
CH340 for WCH). Windows usually installs it automatically the first time the
board is connected.
- **"Failed to connect ... Wrong boot mode"** - hold the **BOOT** button while
the upload starts, and release it when the progress bar appears.
- **Board hangs right after flashing** - on a native-USB ESP32 (C3/S3/S2) the
`while (!Serial) {}` at the top of `setup()` waits for a host to open the port.
Keep the board tethered to a computer, or delete that line.

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
- Should see "WiFi connected!" with IP address
- NTP may or may not sync (`STATUS` reports it); the cloud timestamps come from
  Firebase's server clock, so a missing NTP sync does not affect the data
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
heading the ambulance transmits is not optional. All three boards now default to
the same junction, 13.013123, 77.629112 (JNC001), and the ambulance already
powers up inside the trigger pointing at it:

| Step | Where | Command / check |
| --- | --- | --- |
| 1 | LoRa receiver | Send `STATUS`: expect `wifi: connected` and `config published to Firebase: yes`. (`time synced: no` is harmless - the timestamps come from Firebase's clock) |
| 2 | Traffic signal unit | Optional: send `SET_LAT 13.013123` and `SET_LNG 77.629112` if you moved it |
| 3 | Ambulance ESP32 | Nothing needed - `BENCH_DEMO_START` already powers it up at 13.01582, 77.629112, heading 180, 300 m north of the junction, and it transmits from power-on. Send `SIM <lat>,<lng>,<heading>,<speed>` only to move it elsewhere |
| 4 | Ambulance ESP32 | Expect `[TX] ...` every broadcast interval |
| 5 | LoRa receiver | Expect `AMBULANCE WITHIN TRIGGER ZONE!`, then `[EVENT] type=gps_preempt_started` |
| 6 | Traffic signal unit | Expect the ambulance direction to go green and the cross direction red |
| 7 | Dashboard | Junction panel goes to `priority_active`, event feed shows the preemption, map shows the ambulance |
| 8 | Either | Send `EMERGENCY OFF` on the ambulance, or stop transmitting, and confirm the corridor restores after the packet timeout |

The heading matters: 13.01582, 77.629112 sits north of the junction, so the bearing to
the junction is 180 degrees. A heading of 0 passes the LoRa receiver (it triggers on
distance alone) but the traffic signal unit rejects it as travelling away, and the
light will not change.

If you send your own `SIM` instead of relying on the default, place it within
500 m of 13.013123, 77.629112 and point the heading at that point. Both receivers
now default to the same junction, so a single position satisfies both of them.

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

### The serial console is drowned in repeating driver errors

If the receiver's console fills with `wifi:sta is connecting, cannot set config`
instead of its own output, the board is retrying an unreachable access point
faster than you can read it. Because `Serial.print()` blocks once the 128 byte
TX buffer fills, that also slows the main loop, which is why the symptom shows up
as unreliable timing rather than as a visible error. The sketch now silences the
WiFi driver's own logging and turns off its auto-reconnect, so it retries once
every 10 s and prints `Attempting to reconnect to WiFi...` instead.

To tell a genuine flood from a serial-probe artifact, measure the raw byte rate
rather than eyeballing text - a line physically cannot carry more than `baud/10`
bytes per second, so anything above that means the reading is unreliable, not that
the board is faster:

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/measure-serial-rate.ps1 -Port COM9 -Baud 115200 -Seconds 10
```

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
