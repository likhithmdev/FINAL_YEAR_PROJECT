import { describe, expect, it } from "vitest";
import { ROUTE, advanceDemo, buildDemoWorld } from "./demoScenario";
import {
  deriveCorridor,
  hospitalAlertMeta,
  normalizeAlerts,
  normalizeAmbulances,
  normalizeHospitals,
  normalizeJunctionEvents,
  normalizeJunctions,
  policeAlertMeta,
} from "./model";

// The demo exists so the console can be demonstrated without hardware, which
// makes it the only place the full emergency sequence is expressed end-to-end:
// preemption fires on approach, the RFID clearance is recorded once at the stop
// line, and the signal restores afterwards. If that sequence breaks, the demo
// stops being a faithful rehearsal of the real system.

const TICK_MS = 1000;

function runTicks(world, ticks, stepMs = TICK_MS) {
  let current = world;
  const frames = [];
  for (let i = 0; i < ticks; i += 1) {
    const result = advanceDemo(current, stepMs);
    current = result.world;
    frames.push(result);
  }
  return { world: current, frames };
}

const stopLineAt = (world) => world.lengths[0] + world.lengths[1];

describe("buildDemoWorld", () => {
  it("derives a routable corridor from the route", () => {
    const world = buildDemoWorld();
    expect(world.lengths).toHaveLength(ROUTE.length - 1);
    expect(world.totalMeters).toBeGreaterThan(0);
    expect(world.lengths.every((length) => length > 0)).toBe(true);
    expect(world.totalMeters).toBeCloseTo(
      world.lengths.reduce((sum, length) => sum + length, 0),
      6,
    );
  });

  it("starts already approaching the junction so preemption is visible immediately", () => {
    const world = buildDemoWorld();
    expect(world.cleared).toBe(false);
    expect(stopLineAt(world) - world.distance).toBeCloseTo(470, 5);
    expect(stopLineAt(world) - world.distance).toBeLessThan(500);
  });

  it("seeds the event log with the approach that led to preemption", () => {
    const world = buildDemoWorld();
    expect(world.events.map((event) => event.eventType)).toEqual([
      "gps_preempt_started",
      "approach_tracking",
    ]);
  });
});

describe("advanceDemo — preemption", () => {
  it("preempts the junction as soon as the ambulance is within the threshold", () => {
    const { data } = advanceDemo(buildDemoWorld(), TICK_MS);
    const junction = data.junctions.JNC001;
    expect(junction.signalState).toBe("priority_active");
    expect(junction.preemptionMode).toBe("gps_lora");
    expect(junction.activeAmbulanceId).toBe("AMB001");
    expect(junction.distanceMeters).toBeLessThanOrEqual(500);

    const telemetry = data.ambulances.AMB001.lastLoRaTelemetry;
    expect(telemetry.approaching).toBe(true);
    expect(telemetry.preemptionEligible).toBe(true);
    expect(telemetry.gpsFix).toBe(true);
  });

  it("raises the police alert while the preemption is held", () => {
    const { data } = advanceDemo(buildDemoWorld(), TICK_MS);
    expect(data.policeAlerts.JNC001.TRIP001.status).toBe("preemption_active");
    expect(data.policeAlerts.JNC001.TRIP001.preemptionMode).toBe("gps_lora");
  });

  it("advances the ambulance along the route", () => {
    const world = buildDemoWorld();
    const { world: next } = advanceDemo(world, TICK_MS);
    expect(next.distance).toBeGreaterThan(world.distance);
  });
});

describe("advanceDemo — RFID clearance and restore", () => {
  // 470 m to the stop line at 46 km/h (~12.8 m/s) takes about 37 s.
  const CROSSING_TICKS = 40;

  it("records exactly one clearance and restores the normal cycle", () => {
    const { world, frames } = runTicks(buildDemoWorld(), CROSSING_TICKS);
    const junction = frames.at(-1).data.junctions.JNC001;

    expect(world.cleared).toBe(true);
    expect(junction.signalState).toBe("normal");
    expect(junction.preemptionMode).toBe("none");
    expect(junction.activeAmbulanceId).toBeNull();
    expect(junction.lastDwellTime).toBe("11s");

    const clearances = world.events.filter((event) => event.eventType === "rfid_clearance");
    expect(clearances).toHaveLength(1);
    expect(clearances[0].rfidTagId).toBe("RFID_TAG_001");
    expect(clearances[0].preemptionMode).toBe("rfid_clearance");
  });

  it("does not record a second clearance once the junction is already cleared", () => {
    const { world: cleared } = runTicks(buildDemoWorld(), CROSSING_TICKS);
    expect(cleared.distance).toBeLessThan(cleared.totalMeters);

    const { world: later } = advanceDemo(cleared, TICK_MS);
    expect(later.events.filter((event) => event.eventType === "rfid_clearance")).toHaveLength(1);
  });

  it("orders the sequence preemption -> approach -> clearance", () => {
    const { world } = runTicks(buildDemoWorld(), CROSSING_TICKS);
    const types = world.events.map((event) => event.eventType);
    expect(types.indexOf("gps_preempt_started")).toBeLessThan(types.indexOf("rfid_clearance"));
    expect(types.indexOf("approach_tracking")).toBeLessThan(types.indexOf("rfid_clearance"));
  });

  it("releases the preemption after the ambulance is through", () => {
    const { frames } = runTicks(buildDemoWorld(), CROSSING_TICKS);
    const telemetry = frames.at(-1).data.ambulances.AMB001.lastLoRaTelemetry;
    expect(telemetry.approaching).toBe(false);
    expect(telemetry.preemptionEligible).toBe(false);
    expect(frames.at(-1).data.policeAlerts.JNC001.TRIP001.status).toBe("clearance");
  });
});

describe("advanceDemo — looping", () => {
  it("wraps back to the approach so a long demo keeps showing a corridor", () => {
    const { world: cleared } = runTicks(buildDemoWorld(), 40);
    const startDistance = stopLineAt(cleared) - 470;

    // One oversized step sufficient to run past the end of the route.
    const { world: wrapped } = advanceDemo(cleared, 120 * 1000);
    expect(wrapped.distance).toBeCloseTo(startDistance, 5);
    expect(wrapped.cleared).toBe(false);
  });

  it("resumes preempting after the wrap", () => {
    const { world: cleared } = runTicks(buildDemoWorld(), 40);
    const { world: wrapped } = advanceDemo(cleared, 120 * 1000);
    const { data } = advanceDemo(wrapped, TICK_MS);
    expect(data.junctions.JNC001.signalState).toBe("priority_active");
  });
});

describe("demo scenario through the normalisation pipeline", () => {
  it("produces the shapes the console renders", () => {
    const { frames } = runTicks(buildDemoWorld(), 40);
    const data = frames.at(-1).data;

    const ambulances = normalizeAmbulances(data.ambulances);
    expect(ambulances).toHaveLength(1);
    expect(ambulances[0].ambulanceId).toBe("AMB001");
    expect(ambulances[0].emergencyActive).toBe(true);
    expect(ambulances[0].severity).toBe("Critical");

    const junctions = normalizeJunctions(data.junctions);
    expect(junctions.map((junction) => junction.junctionId)).toEqual(["JNC001", "JNC002", "JNC003"]);
    // JNC003 is deliberately stale in the demo, so exactly two are reachable.
    expect(junctions.filter((junction) => junction.online)).toHaveLength(2);

    const hospitals = normalizeHospitals(data.hospitals);
    expect(hospitals).toHaveLength(3);

    const corridor = deriveCorridor({ ambulance: ambulances[0], junctions, hospitals });
    expect(corridor.destination.name).toBe("City Care Hospital");
    expect(corridor.nearestJunction.junctionId).toBe("JNC001");
    expect(corridor.hospitalMeters).toBeGreaterThan(0);
    expect(corridor.hospitalEta).toBeGreaterThan(0);
  });

  it("still renders the junction as online while it is preempting", () => {
    const { frames } = runTicks(buildDemoWorld(), 5);
    const junctions = normalizeJunctions(frames.at(-1).data.junctions);
    const jnc001 = junctions.find((junction) => junction.junctionId === "JNC001");
    expect(jnc001.signalState).toBe("priority_active");
    expect(jnc001.online).toBe(true);
  });

  it("exposes the police and hospital alert streams", () => {
    const { frames } = runTicks(buildDemoWorld(), 40);
    const data = frames.at(-1).data;

    const police = normalizeAlerts(data.policeAlerts, { kind: "police", meta: policeAlertMeta });
    expect(police).toHaveLength(1);
    expect(police[0].id).toBe("JNC001:TRIP001");
    expect(police[0].label).toBe("Clearing");

    const hospital = normalizeAlerts(data.hospitalAlerts, { kind: "hospital", meta: hospitalAlertMeta });
    expect(hospital).toHaveLength(1);
    expect(hospital[0].id).toBe("HOSP001:TRIP001");
    expect(hospital[0].label).toBe("Incoming");
    expect(hospital[0].bayReadiness.bedBay).toBe(true);
  });

  it("namespaces the demo events the same way live Firebase rows are named", () => {
    const { frames } = runTicks(buildDemoWorld(), 40);
    const events = normalizeJunctionEvents(frames.at(-1).data.junctionEvents);
    expect(events.every((event) => event.id.startsWith("fb:"))).toBe(true);
    expect(events.some((event) => event.eventType === "rfid_clearance")).toBe(true);
    // Newest first.
    expect(events[0].timestamp).toBeGreaterThanOrEqual(events.at(-1).timestamp);
  });
});
