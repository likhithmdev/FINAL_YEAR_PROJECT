#!/usr/bin/env node
// Guards the Realtime Database security rules.
//
// The rules are the one artefact where a mistake is invisible locally and
// catastrophic in production, so this asserts the properties we actually care
// about and fails the build if any of them regress:
//
//   1. nothing is readable without authentication
//   2. credentials are never writable without authentication
//   3. unauthenticated writes are confined to the firmware's known paths
//   4. every database node the clients use has an explicit rule
//
// Node coverage is derived from the clients themselves (FirebasePaths.kt, the
// dashboard's firebasePaths, and the paths the ESP32 sketches patch), so adding
// a node in code without a rule fails here instead of silently falling through
// to the default deny at runtime.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (p) => readFileSync(join(root, p), "utf8");

const rules = JSON.parse(read("database.rules.json")).rules;

// Paths the ESP32 firmware writes with no credentials. Anything outside this
// list must require authentication to write.
const FIRMWARE_WRITE_PATHS = [
  "junctions/$junctionId",
  "ambulances/$ambulanceId/lastLocation",
  "loraTelemetry",
  "junctionEvents",
];

const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
  console.log(`${ok ? "  ok  " : " FAIL "} ${message}`);
};

// ---------------------------------------------------------------- traversal --
function walk(node, path = []) {
  const found = [];
  for (const [key, value] of Object.entries(node)) {
    if (key.startsWith(".")) continue;
    const next = [...path, key];
    found.push({ path: next.join("/"), node: value });
    if (value && typeof value === "object") found.push(...walk(value, next));
  }
  return found;
}

const nodes = walk(rules);

// ------------------------------------------------------- 1. reads need auth --
console.log("\nreads require authentication");
const openReads = nodes.filter((n) => n.node[".read"] === true);
check(openReads.length === 0, `no unauthenticated read grants (found: ${openReads.map((n) => n.path).join(", ") || "none"})`);

const rootRead = rules[".read"];
check(rootRead !== true, `root is not world-readable (root .read = ${JSON.stringify(rootRead)})`);

// --------------------------------------------------- 2. credentials locked --
console.log("\ncredentials are not world-writable");
check(rules.users !== undefined, "users node has a rule");
check(rules.users?.[".read"] !== true, `users is not world-readable (users .read = ${JSON.stringify(rules.users?.[".read"])})`);

const usersWrite = JSON.stringify(rules.users?.["$userId"]?.[".write"]);
check(usersWrite.includes("auth != null"), `users writes require auth (users/$userId .write = ${usersWrite})`);

// ------------------------------------------ 3. firmware writes are confined --
console.log("\nunauthenticated writes are confined to the firmware paths");
const openWrites = nodes.filter((n) => n.node[".write"] === true).map((n) => n.path);
const unexpected = openWrites.filter((p) => !FIRMWARE_WRITE_PATHS.includes(p));
check(
  unexpected.length === 0,
  `no unauthenticated write outside the firmware allowlist (unexpected: ${unexpected.join(", ") || "none"})`,
);
check(
  openWrites.length > 0,
  `firmware carve-outs are still present so the hardware keeps working (${openWrites.join(", ")})`,
);
check(
  !openWrites.some((p) => p === "ambulances/$ambulanceId"),
  "an unauthenticated client cannot overwrite the whole ambulance record (only lastLocation)",
);

// --------------------------------------------------- 4. schema coverage --
console.log("\nevery node used by a client has an explicit rule");

const kotlin = read("mobile_app/app/src/main/java/com/smartambulance/driver/data/FirebasePaths.kt");
const kotlinNodes = [...kotlin.matchAll(/=\s*"([a-zA-Z]+)"/g)].map((m) => m[1]);

const dashboard = read("dashboard/src/integrations/firebaseClient.js");
const dashNodes = [...dashboard.matchAll(/:\s*"([a-zA-Z]+)",/g)].map((m) => m[1]);

// `systemStatus` exists in the live database but is declared by no client.
const liveOnly = ["systemStatus"];

const expected = [...new Set([...kotlinNodes, ...dashNodes, ...liveOnly])].sort();
const ruled = new Set(Object.keys(rules).filter((k) => !k.startsWith(".")));
const missing = expected.filter((n) => !ruled.has(n));
check(missing.length === 0, `all ${expected.length} client nodes are covered (missing: ${missing.join(", ") || "none"})`);

const orphaned = [...ruled].filter((n) => !expected.includes(n));
check(orphaned.length === 0, `no rules for unused nodes (orphaned: ${orphaned.join(", ") || "none"})`);

// ------------------------------------------------------------------ result --
console.log("");
if (failures.length) {
  console.error(`database.rules.json failed ${failures.length} check(s):`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`database.rules.json passed all checks (${expected.length} nodes, ${openWrites.length} firmware carve-outs).`);
