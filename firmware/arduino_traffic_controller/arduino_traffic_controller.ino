/*
  Arduino Traffic Controller - junction signal head + RFID readers

  This is the sketch the bench wiring was built around, so the hardware flow is
  unchanged from the version that already runs on the board:

    - 4 signal heads driven straight from digital pins
    - two MFRC522 readers, one on the approach (ENTRY) and one on the exit
    - a 5 s green cycle with a 1 s yellow step between signals
    - an ambulance tag on ENTRY holds its arm green until the tag is seen on
      EXIT, or until the roadside ESP32 reports the vehicle has gone

  What is new here: the board now actually reads its serial port. The roadside
  ESP32 (lora_receiver_esp32) has always sent "AMBULANCE_APPROACH,<n>" and
  "AMBULANCE_OUT_OF_RANGE" at 9600 baud, but nothing on the Uno parsed them, so
  LoRa preemption could never reach the lights. The RFID flow is untouched.

  ---------------------------------------------------------------------------
  WIRING
  ---------------------------------------------------------------------------

  Signal | RED | GREEN | YELLOW
  -------+-----+-------+-------
    0    | D2  | D3    | A3
    1    | D4  | D5    | A4
    2    | D6  | A1    | A5
    3    | A0  | A2    | (none - no yellow on this arm)

  RFID ENTRY reader : SS = D8,  RST = D7
  RFID EXIT  reader : SS = D10, RST = D9
  Roadside ESP32    : ESP32 TX2 (GPIO17) -> Uno pin 0 (RX)
                      ESP32 RX2 (GPIO16) <- Uno pin 1 (TX)

  Upload gotcha: the ESP32's TX pin idles high on the Uno's RX line, which is
  the same line the USB bridge drives. That contention makes uploads fail
  intermittently with "stk500_getsync(): not in sync". Unplug the
  ESP32 -> Uno RX wire (or power the ESP32 down) while uploading.

  ---------------------------------------------------------------------------
  SERIAL CONTRACT (9600 baud)
  ---------------------------------------------------------------------------

  Received from the ESP32:
    "AMBULANCE_APPROACH,<signal_id>"  preempt this arm and hold it green
    "AMBULANCE_OUT_OF_RANGE"          vehicle left, restart the normal cycle
    "AMBULANCE_EXIT"                  manual "restore normal traffic now"

  Printed for the ESP32 to read back - do not reword these two lines, because
  lora_receiver_esp32.ino matches them by substring in pollArduinoUart():
    "AMBULANCE EXIT DETECTED (RFID)"  clearance seen on the EXIT reader
    "UNAUTHORIZED RFID tag"           tag not present in allowedUIDs
*/

#include <SPI.h>
#include <MFRC522.h>


// =====================================================
// RFID CONFIGURATION
// =====================================================

// Reader 2 = ENTRY (approach)
#define SS_PIN1 8
#define RST_PIN1 7

// Reader 1 = EXIT
#define SS_PIN2 10
#define RST_PIN2 9

MFRC522 entryRFID(SS_PIN1, RST_PIN1); // Entry reader
MFRC522 exitRFID(SS_PIN2, RST_PIN2);  // Exit reader


// =====================================================
// SIGNAL LED CONFIGURATION
// =====================================================

// 4 signals (RED + GREEN); the first 3 also have a YELLOW

int redLED[4] = {
  2, 4, 6, A0
};

int greenLED[4] = {
  3, 5, A1, A2
};

int yellowLED[3] = {
  A3, A4, A5
};


// =====================================================
// AUTHORIZED AMBULANCE RFID TAG
// =====================================================

String allowedUIDs[] = {
  "04C0F0F2021390"
};

int totalTags = 1;


// =====================================================
// TRAFFIC VARIABLES
// =====================================================

int currentSignal = 0;

// Green time for each signal
unsigned long cycleTime = 5000;

unsigned long lastSwitch = 0;


// =====================================================
// AMBULANCE VARIABLES
// =====================================================

bool ambulanceActive = false;

// True when the ESP32 put us in ambulance mode. "AMBULANCE_OUT_OF_RANGE" must
// only release a hold the ESP32 created - if the hold came from an RFID tag on
// ENTRY, an out-of-range report from the receiver must not cancel it.
bool uartTriggered = false;

// Which signal should become GREEN when an ambulance arrives
int entrySignalID = 0;


// =====================================================
// UART COMMAND PROCESSING
// =====================================================

String uartBuffer = "";
unsigned long lastUartActivity = 0;

void processUartCommand(String command) {

  command.trim();

  if (command.length() == 0) return;

  Serial.print("Received UART command: ");
  Serial.println(command);

  if (command.startsWith("AMBULANCE_APPROACH,")) {

    int signalId = command.substring(19).toInt();

    if (signalId >= 0 && signalId < 4) {

      Serial.println("================================");
      Serial.println("AMBULANCE APPROACHING FROM ESP32");
      Serial.print("Preempting Signal ");
      Serial.println(signalId);
      Serial.println("================================");

      ambulanceActive = true;
      uartTriggered = true;
      entrySignalID = signalId;
      currentSignal = signalId;

      setSignal(currentSignal);

      Serial.print("Signal ");
      Serial.print(currentSignal);
      Serial.println(" WILL STAY GREEN");
    }
  }
  else if (command == "AMBULANCE_OUT_OF_RANGE") {

    if (ambulanceActive && uartTriggered) {

      Serial.println("================================");
      Serial.println("AMBULANCE OUT OF RANGE (ESP32)");
      Serial.println("RESTORING NORMAL TRAFFIC");
      Serial.println("================================");

      ambulanceActive = false;
      uartTriggered = false;

      // Restart the normal cycle timer
      lastSwitch = millis();

      setSignal(currentSignal);
    }
  }
  else if (command == "AMBULANCE_EXIT") {

    // Manual "restore now", for a serial console or a host script.
    if (ambulanceActive) {

      Serial.println("================================");
      Serial.println("AMBULANCE EXIT (SERIAL COMMAND)");
      Serial.println("RESTORING NORMAL TRAFFIC");
      Serial.println("================================");

      ambulanceActive = false;
      uartTriggered = false;

      lastSwitch = millis();

      setSignal(currentSignal);
    }
  }
}

void readUartCommands() {

  while (Serial.available() > 0) {

    char c = Serial.read();

    if (c == '\n' || c == '\r') {

      if (uartBuffer.length() > 0) {

        processUartCommand(uartBuffer);
        uartBuffer = "";
      }
    }
    else {

      uartBuffer += c;
      lastUartActivity = millis();
    }
  }

  // Drop a partial command if the sender stopped mid-line
  if (uartBuffer.length() > 0 && millis() - lastUartActivity > 100) {

    uartBuffer = "";
  }
}


// =====================================================
// SETUP
// =====================================================

void setup() {

  Serial.begin(9600);

  Serial.println();
  Serial.println("================================");
  Serial.println("TRAFFIC + AMBULANCE SYSTEM");
  Serial.println("With ESP32 UART Integration");
  Serial.println("================================");

  SPI.begin();

  // Disable both RFID readers initially
  pinMode(SS_PIN1, OUTPUT);
  pinMode(SS_PIN2, OUTPUT);

  digitalWrite(SS_PIN1, HIGH);
  digitalWrite(SS_PIN2, HIGH);

  delay(100);

  // Initialize RFID readers
  Serial.println("Initializing ENTRY RFID...");
  entryRFID.PCD_Init();
  delay(50);

  Serial.println("Initializing EXIT RFID...");
  exitRFID.PCD_Init();
  delay(50);


  // Signal LEDs
  for (int i = 0; i < 4; i++) {

    pinMode(redLED[i], OUTPUT);
    pinMode(greenLED[i], OUTPUT);

    digitalWrite(redLED[i], LOW);
    digitalWrite(greenLED[i], LOW);
  }


  // Yellow LEDs
  for (int i = 0; i < 3; i++) {

    pinMode(yellowLED[i], OUTPUT);

    digitalWrite(yellowLED[i], LOW);
  }


  // Start with Signal 0 GREEN
  currentSignal = 0;

  setSignal(currentSignal);

  lastSwitch = millis();

  Serial.println();
  Serial.println("SYSTEM READY");
  Serial.println("ENTRY = RFID Reader 2");
  Serial.println("EXIT  = RFID Reader 1");
  Serial.println("UART  = Connected to ESP32");
  Serial.println("================================");
}


// =====================================================
// LOOP
// =====================================================

void loop() {

  // Service the ESP32 first, in every mode
  readUartCommands();


  // =================================================
  // AMBULANCE MODE
  // =================================================

  if (ambulanceActive) {

    // Only check the EXIT reader; the held arm stays green until either the
    // EXIT reader clears it or the ESP32 reports the vehicle is gone.
    if (checkRFID(exitRFID, SS_PIN2)) {

      Serial.println();
      Serial.println("********************************");
      Serial.println("AMBULANCE EXIT DETECTED (RFID)");
      Serial.println("NORMAL TRAFFIC RESUMING");
      Serial.println("********************************");

      ambulanceActive = false;
      uartTriggered = false;

      lastSwitch = millis();

      setSignal(currentSignal);
    }

    return;
  }


  // =================================================
  // NORMAL MODE
  // =================================================

  // Reader 2 = ENTRY
  if (checkRFID(entryRFID, SS_PIN1)) {

    Serial.println();
    Serial.println("********************************");
    Serial.println("AMBULANCE DETECTED AT ENTRY (RFID)");
    Serial.println("STOPPING NORMAL TRAFFIC");
    Serial.println("********************************");

    ambulanceActive = true;
    uartTriggered = false; // this hold came from the tag, not the ESP32

    // Ambulance gets Signal 0 GREEN
    currentSignal = entrySignalID;

    setSignal(currentSignal);

    Serial.print("Signal ");
    Serial.print(currentSignal);
    Serial.println(" WILL STAY GREEN");

    return;
  }


  // =================================================
  // NORMAL TRAFFIC CYCLE
  // =================================================

  if (millis() - lastSwitch >= cycleTime) {

    changeToNextSignal();

    lastSwitch = millis();
  }
}


// =====================================================
// CHANGE TO NEXT SIGNAL
// =====================================================

void changeToNextSignal() {

  int previousSignal = currentSignal;


  // -------------------------------------------------
  // Turn previous GREEN OFF
  // -------------------------------------------------

  digitalWrite(greenLED[previousSignal], LOW);


  // -------------------------------------------------
  // Yellow before changing
  // -------------------------------------------------

  if (previousSignal < 3) {

    // Make sure RED is OFF
    digitalWrite(redLED[previousSignal], LOW);

    // Turn YELLOW ON
    digitalWrite(yellowLED[previousSignal], HIGH);

    Serial.print("Signal ");
    Serial.print(previousSignal);
    Serial.println(" -> YELLOW");

    delay(1000);

    // Turn YELLOW OFF
    digitalWrite(yellowLED[previousSignal], LOW);
  }


  // -------------------------------------------------
  // Move to next signal
  // -------------------------------------------------

  currentSignal++;

  if (currentSignal >= 4) {
    currentSignal = 0;
  }


  // -------------------------------------------------
  // Set next signal
  // -------------------------------------------------

  setSignal(currentSignal);
}


// =====================================================
// SET SIGNAL
// =====================================================

// One signal = GREEN
// All other signals = RED
// No RED + YELLOW combination

void setSignal(int signal) {

  // -------------------------------------------------
  // Turn OFF ALL LEDs first
  // -------------------------------------------------

  for (int i = 0; i < 4; i++) {

    digitalWrite(redLED[i], LOW);
    digitalWrite(greenLED[i], LOW);
  }

  for (int i = 0; i < 3; i++) {

    digitalWrite(yellowLED[i], LOW);
  }


  // -------------------------------------------------
  // Selected signal = GREEN
  // Other signals = RED
  // -------------------------------------------------

  for (int i = 0; i < 4; i++) {

    if (i == signal) {

      digitalWrite(redLED[i], LOW);
      digitalWrite(greenLED[i], HIGH);

    }
    else {

      digitalWrite(greenLED[i], LOW);
      digitalWrite(redLED[i], HIGH);
    }
  }


  Serial.print("Setting Signal ");
  Serial.print(signal);
  Serial.println(" GREEN");
}


// =====================================================
// RFID VERIFICATION
// =====================================================

bool checkRFID(MFRC522 &rfid, int ssPin) {

  bool success = false;


  // -------------------------------------------------
  // Disable BOTH readers
  // -------------------------------------------------

  digitalWrite(SS_PIN1, HIGH);
  digitalWrite(SS_PIN2, HIGH);


  // -------------------------------------------------
  // Enable requested reader
  // -------------------------------------------------

  digitalWrite(ssPin, LOW);


  // -------------------------------------------------
  // Check for RFID card
  // -------------------------------------------------

  if (rfid.PICC_IsNewCardPresent() &&
      rfid.PICC_ReadCardSerial()) {

    String scannedUID = "";


    // Build UID
    for (byte i = 0; i < rfid.uid.size; i++) {

      if (rfid.uid.uidByte[i] < 0x10) {
        scannedUID += "0";
      }

      scannedUID += String(
        rfid.uid.uidByte[i],
        HEX
      );
    }


    scannedUID.toUpperCase();


    // Print UID
    Serial.print("Scanned UID: ");
    Serial.println(scannedUID);


    // -------------------------------------------------
    // Compare UID
    // -------------------------------------------------

    for (int i = 0; i < totalTags; i++) {

      if (scannedUID == allowedUIDs[i]) {

        success = true;

        if (ssPin == SS_PIN1) {

          Serial.println("Ambulance detected at ENTRY");

        }
        else {

          Serial.println("Ambulance EXIT detected");
        }

        break;
      }
    }


    // Authorized
    if (success) {

      Serial.println("AUTHORIZED RFID");
    }
    // Unauthorized. The receiver greps for "UNAUTHORIZED RFID", so the wording
    // here is load-bearing even though the tag is rejected either way.
    else {

      Serial.println("UNAUTHORIZED RFID tag");
    }


    // Stop RFID communication
    rfid.PICC_HaltA();
    rfid.PCD_StopCrypto1();
  }


  // -------------------------------------------------
  // Disable reader
  // -------------------------------------------------

  digitalWrite(ssPin, HIGH);


  return success;
}
