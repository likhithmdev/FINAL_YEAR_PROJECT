#!/usr/bin/env node
// Guards the UART contract between the roadside ESP32 and the junction Arduino.
//
// The two boards share one 9600 baud line, and neither can compile-check the
// other's strings. A silent mismatch does not break the build - it silently
// removes the preemption path, which is exactly what happened here: the Arduino
// sketch printed "AMBULANCE EXIT DETECTED" while the receiver grepped for
// "EXIT DETECTED (RFID)", so an RFID stop-line release never reached the cloud,
// and the Arduino never read its serial port at all, so "AMBULANCE_APPROACH"
// was a dead letter in the other direction.
//
// Every expectation below is derived from the sketches themselves, so renaming
// a command or a marker on one side fails here instead of at the junction:
//
//   1. the Arduino actually reads its serial port
//   2. the receiver actually reads the Arduino's replies back
//   3. every marker the receiver greps appears in something the Arduino prints
//   4. every command the receiver sends is one the Arduino parses
//   5. the command offsets the Arduino uses still match those command names
//   6. both ends agree on the baud rate

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (p) => readFileSync(join(root, p), "utf8");

const ARDUINO_PATH = "firmware/arduino_traffic_controller/arduino_traffic_controller.ino";
const RECEIVER_PATH = "firmware/lora_receiver_esp32/lora_receiver_esp32.ino";

const arduino = read(ARDUINO_PATH);
const receiver = read(RECEIVER_PATH);

const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
  console.log(`${ok ? "  ok  " : " FAIL "} ${message}`);
};

// Strip // and /* */ comments so commented-out literals cannot satisfy a check.
const stripComments = (src) =>
  src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

const arduinoCode = stripComments(arduino);
const receiverCode = stripComments(receiver);

const stringLiterals = (src, regex) =>
  [...src.matchAll(regex)].map((m) => m[1]);

// ------------------------------------------------- 1. the Arduino listens in --
console.log("\nthe junction Arduino reads the ESP32's commands");
check(
  /Serial\.available\s*\(\s*\)/.test(arduinoCode) && /Serial\.read\s*\(\s*\)/.test(arduinoCode),
  "the Arduino reads its serial port (Serial.available/Serial.read present)"
);

const arduinoHandlers = [
  ...stringLiterals(arduinoCode, /command\.startsWith\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g),
  ...stringLiterals(arduinoCode, /command\s*==\s*"((?:[^"\\]|\\.)*)"/g),
];
check(
  arduinoHandlers.length > 0,
  `the Arduino parses at least one command (found: ${arduinoHandlers.join(", ") || "none"})`
);

// ------------------------------------------- 2. the receiver listens back in --
console.log("\nthe roadside ESP32 reads the Arduino's replies");
check(
  /ARDUINO_UART\.available\s*\(\s*\)/.test(receiverCode) &&
    /ARDUINO_UART\.read\s*\(\s*\)/.test(receiverCode),
  "the receiver reads the Arduino's serial output back (ARDUINO_UART.available/read present)"
);

// ------------------------------------------------ 3. grepped markers exist ----
console.log("\nevery reply the receiver greps for is one the Arduino prints");
const arduinoPrints = stringLiterals(
  arduinoCode,
  /Serial\.print(?:ln)?\(\s*"((?:[^"\\]|\\.)*)"/g
);
const greppedMarkers = stringLiterals(
  receiverCode,
  /line\.indexOf\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g
);
check(greppedMarkers.length > 0, `the receiver greps at least one marker (found: ${greppedMarkers.join(", ") || "none"})`);
for (const marker of greppedMarkers) {
  check(
    arduinoPrints.some((printed) => printed.includes(marker)),
    `the Arduino prints a line containing "${marker}"`
  );
}

// ------------------------------------------------- 4. sent commands handled ----
console.log("\nevery command the receiver sends is one the Arduino parses");
const sentCommands = [
  ...stringLiterals(receiverCode, /sendToArduino\(\s*"((?:[^"\\]|\\.)*)"\s*\)/g),
  // "AMBULANCE_APPROACH,%d" -> the literal prefix the Arduino must match
  ...stringLiterals(receiverCode, /snprintf\([^,]+,\s*sizeof\([^)]*\)\s*,\s*"((?:[^"\\]|\\.)*)"/g).map(
    (format) => format.replace(/%.*$/, "")
  ),
];
check(sentCommands.length > 0, `the receiver sends at least one command (found: ${sentCommands.join(", ") || "none"})`);
for (const command of sentCommands) {
  check(
    arduinoHandlers.includes(command),
    `the Arduino handles "${command}"`
  );
}

// --------------------------------- 5. substrings still line up with names ----
console.log("\nthe Arduino's fixed offsets still match the command names");
const offsetMatch = arduinoCode.match(/command\.substring\(\s*(\d+)\s*\)/);
if (offsetMatch) {
  const offset = Number(offsetMatch[1]);
  const prefixed = arduinoHandlers.filter((h) => h.endsWith(","));
  check(
    prefixed.length > 0,
    `a length-prefixed command is handled (found: ${prefixed.join(", ") || "none"})`
  );
  for (const command of prefixed) {
    check(
      command.length === offset,
      `substring(${offset}) skips exactly "${command}" (${command.length} chars)`
    );
  }
} else {
  check(false, "no command.substring() offset to verify - update this guard if the parsing style changed");
}

// ------------------------------------------------------------- 6. baud rate ---
console.log("\nboth ends agree on the baud rate");
const arduinoBaud = arduinoCode.match(/Serial\.begin\(\s*(\d+)\s*\)/);
const receiverBaud = receiverCode.match(/#define\s+ARDUINO_BAUD\s+(\d+)/);
check(!!arduinoBaud, "the Arduino's Serial.begin() baud is discoverable");
check(!!receiverBaud, "the receiver's ARDUINO_BAUD is discoverable");
if (arduinoBaud && receiverBaud) {
  check(
    arduinoBaud[1] === receiverBaud[1],
    `both ends run at ${arduinoBaud[1]} baud (Arduino ${arduinoBaud[1]}, receiver ${receiverBaud[1]})`
  );
}

// ------------------------------------------------------------------ verdict ---
if (failures.length > 0) {
  console.error(`\n${failures.length} UART contract check(s) failed:`);
  for (const failure of failures) console.error(`  - ${failure}`);
  process.exit(1);
}
console.log("\nUART contract intact.");
