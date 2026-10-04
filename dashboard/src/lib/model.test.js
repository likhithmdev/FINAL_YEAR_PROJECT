import { describe, expect, it } from "vitest";
import { etaSeconds } from "./geo";
import {
  deriveCorridor,
  eventLabel,
  eventTone,
  hospitalAlertMeta,
  normalizeAlerts,
  normalizeAmbulances,
  normalizeHospitals,
  normalizeJunctionEvents,
  normalizeJunctions,
  normalizeMqttEvent,
  normalizeTrips,
  policeAlertMeta,
  severityLabel,
  severityTone,
} from "./model";

const NOW = 1800000000000;

describe("severity helpers", () => {
  it("maps the three triage levels", () => {
    expect(severityTone("Critical")).toBe("danger");
    expect(severityTone("Serious")).toBe("amber");
    expect(severityTone("Moderate")).toBe("info");
    expect(severityLabel("Critical")).toBe("P1 · Critical");
    expect(severityLabel("Serious")).toBe("P2 · Serious");
    expect(severityLabel("Moderate")).toBe("P3 · Moderate");
  });

  it("tolerates casing and unknown values", () => {
    expect(severityTone("critical")).toBe("danger");
    expect(severityLabel("Unspecified")).toBe("Unspecified");
    expect(severityLabel(null)).toBe("Unspecified");
    expect(severityTone("Something else")).toBe("neutral");
  });
});

describe("normalizeJunctions", () => {
  it("always exposes the known junctions, even with no database data", () => {
    const junctions = normalizeJunctions({}, { nowMs: NOW });
    expect(junctions.map((j) => j.junctionId)).toEqual(["JNC001", "JNC002", "JNC003"]);
    // Fallback junctions have no timestamp, so they are treated as reachable.
    expect(junctions.every((j) => j.online)).toBe(true);
    expect(junctions[0].name).toBe("Main Road Junction");
  });

  it("applies the firmware's preemption defaults", () => {
    const [jnc] = normalizeJunctions({}, { nowMs: NOW });
    expect(jnc.approachThresholdMeters).toBe(500);
    expect(jnc.bearingToleranceDeg).toBe(35);
    expect(jnc.rssiFallbackThresholdDbm).toBe(-65);
    expect(jnc.gpsPacketTimeoutMs).toBe(5000);
    expect(jnc.clearanceTimeoutMs).toBe(90000);
    expect(jnc.signalState).toBe("normal");
    expect(jnc.preemptionMode).toBe("none");
  });

  it("lets database values win over the fallback catalogue", () => {
    const [jnc] = normalizeJunctions(
      {
        JNC001: {
          name: "MG Road Signal",
          activeLane: "northbound",
          signalState: "priority_active",
          distanceMeters: 138,
          rssi: -66,
          updatedAt: NOW - 100000,
          approachThresholdMeters: 800,
          bearingToleranceDeg: 40,
          rssiFallbackThresholdDbm: -70,
          gpsPacketTimeoutMs: 6000,
          clearanceTimeoutMs: 120000,
        },
      },
      { nowMs: NOW },
    );
    expect(jnc.name).toBe("MG Road Signal");
    expect(jnc.lane).toBe("Northbound");
    expect(jnc.signalState).toBe("priority_active");
    expect(jnc.distanceMeters).toBe(138);
    expect(jnc.rssi).toBe(-66);
    expect(jnc.approachThresholdMeters).toBe(800);
    expect(jnc.bearingToleranceDeg).toBe(40);
    expect(jnc.rssiFallbackThresholdDbm).toBe(-70);
    expect(jnc.gpsPacketTimeoutMs).toBe(6000);
    expect(jnc.clearanceTimeoutMs).toBe(120000);
    expect(jnc.ageSeconds).toBe(100);
  });

  it("treats a junction as online when preempting or recently heard from", () => {
    const byId = Object.fromEntries(
      normalizeJunctions(
        {
          JNC001: { signalState: "priority_active", updatedAt: NOW - 900000 },
          JNC002: { signalState: "normal", updatedAt: NOW - 100000 },
          JNC003: { signalState: "normal", updatedAt: NOW - 700000 },
        },
        { nowMs: NOW },
      ).map((j) => [j.junctionId, j]),
    );
    // Priority beats staleness — the signal is provably alive mid-preemption.
    expect(byId.JNC001.online).toBe(true);
    expect(byId.JNC002.online).toBe(true);
    expect(byId.JNC003.online).toBe(false);
  });

  it("honours an explicit esp32Online flag", () => {
    const byId = Object.fromEntries(
      normalizeJunctions({ JNC003: { esp32Online: true, updatedAt: NOW - 900000 } }, { nowMs: NOW }).map((j) => [
        j.junctionId,
        j,
      ]),
    );
    expect(byId.JNC003.online).toBe(true);
  });

  it("reads coordinates from either the Android or dashboard field names", () => {
    const byId = Object.fromEntries(
      normalizeJunctions(
        {
          JNC001: { lat: 12.5, lng: 77.5 },
          JNC002: { latitude: 12.6, longitude: 77.6 },
        },
        { nowMs: NOW },
      ).map((j) => [j.junctionId, j]),
    );
    expect(byId.JNC001.location).toEqual({ lat: 12.5, lng: 77.5 });
    expect(byId.JNC002.location).toEqual({ lat: 12.6, lng: 77.6 });
    // JNC003 has no database coordinates, so the fallback keeps the marker sane.
    expect(byId.JNC003.location).toEqual({ lat: 12.9668, lng: 77.6072 });
  });
});

describe("normalizeHospitals", () => {
  it("falls back to the demo catalogue", () => {
    const hospitals = normalizeHospitals({});
    expect(hospitals.map((h) => h.hospitalId)).toEqual(["HOSP001", "HOSP002", "HOSP003"]);
    expect(hospitals[0].name).toBe("City Care Hospital");
    expect(hospitals[0].bedsAvailable).toBeNull();
    expect(hospitals[0].emergencyAvailable).toBe(true);
    expect(hospitals[0].location).toEqual({ lat: 12.9698, lng: 77.6015 });
  });

  it("overrides fallbacks with database values", () => {
    const [hosp] = normalizeHospitals({
      HOSP001: {
        name: "Renamed Trauma Centre",
        bedsAvailable: 4,
        emergencyAvailable: false,
        latitude: 12.5,
        longitude: 77.5,
        phone: "+91 80 0000 0000",
        address: "Somewhere",
        distance: 1200,
        eta: "3 min",
      },
    });
    expect(hosp.name).toBe("Renamed Trauma Centre");
    expect(hosp.bedsAvailable).toBe(4);
    expect(hosp.emergencyAvailable).toBe(false);
    expect(hosp.location).toEqual({ lat: 12.5, lng: 77.5 });
    expect(hosp.phone).toBe("+91 80 0000 0000");
    expect(hosp.address).toBe("Somewhere");
    expect(hosp.staticDistance).toBe(1200);
    expect(hosp.staticEta).toBe("3 min");
  });
});

describe("normalizeAmbulances", () => {
  it("infers an active emergency from the status string alone", () => {
    const [amb] = normalizeAmbulances({ AMB001: { ambulanceId: "AMB001", status: "emergency_active" } });
    expect(amb.emergencyActive).toBe(true);
  });

  it("prefers the explicit emergencyActive flag", () => {
    const [amb] = normalizeAmbulances({
      AMB001: { ambulanceId: "AMB001", status: "available", emergencyActive: true },
    });
    expect(amb.emergencyActive).toBe(true);
  });

  it("defaults to available with no emergency", () => {
    const [amb] = normalizeAmbulances({ AMB001: {} });
    expect(amb.ambulanceId).toBe("AMB001");
    expect(amb.status).toBe("available");
    expect(amb.emergencyActive).toBe(false);
    expect(amb.severity).toBe("");
  });

  it("flattens LoRa telemetry and fills absent fields with null", () => {
    const [amb] = normalizeAmbulances(
      {
        AMB001: {
          ambulanceId: "AMB001",
          severity: "critical",
          lastLoRaTelemetry: { junctionId: "JNC001", rssi: -66, distanceMeters: 138, approaching: true },
        },
      },
      { nowMs: NOW },
    );
    expect(amb.severity).toBe("Critical");
    expect(amb.telemetry.junctionId).toBe("JNC001");
    expect(amb.telemetry.rssi).toBe(-66);
    expect(amb.telemetry.distanceMeters).toBe(138);
    expect(amb.telemetry.approaching).toBe(true);
    expect(amb.telemetry.speedKmph).toBeNull();
    expect(amb.telemetry.gpsFix).toBeNull();
  });

  it("normalises lastLocation and stamps its age", () => {
    const [amb] = normalizeAmbulances(
      {
        AMB001: {
          lastLocation: { lat: 12.97, lng: 77.59, source: "android_gps", updatedAt: NOW - 5000 },
          updatedAt: NOW - 5000,
        },
      },
      { nowMs: NOW },
    );
    expect(amb.lastLocation).toEqual({
      lat: 12.97,
      lng: 77.59,
      source: "android_gps",
      updatedAt: NOW - 5000,
    });
    // Freshness tracks the vehicle record, not the last GPS fix, so a stale
    // position does not by itself make the ambulance look offline.
    expect(amb.ageSeconds).toBe(5);
    expect(amb.lastLocation.updatedAt).toBe(NOW - 5000);
  });

  it("reports no vehicle age when the record itself has no timestamp", () => {
    const [amb] = normalizeAmbulances(
      { AMB001: { lastLocation: { lat: 12.97, lng: 77.59, updatedAt: NOW - 5000 } } },
      { nowMs: NOW },
    );
    expect(amb.ageSeconds).toBeNull();
  });

  it("sorts by id for stable rendering", () => {
    const ids = normalizeAmbulances({ AMB002: {}, AMB001: {}, AMB010: {} }).map((a) => a.ambulanceId);
    expect(ids).toEqual(["AMB001", "AMB002", "AMB010"]);
  });
});

describe("normalizeTrips", () => {
  it("orders newest first and defaults unknown statuses", () => {
    const trips = normalizeTrips({
      TRIP001: { tripId: "TRIP001", startedAt: NOW - 60000, status: "active" },
      TRIP002: { tripId: "TRIP002", startedAt: NOW - 1000 },
    });
    expect(trips.map((t) => t.tripId)).toEqual(["TRIP002", "TRIP001"]);
    expect(trips[0].status).toBe("unknown");
    expect(trips[0].endedAt).toBeNull();
    expect(trips[1].endedAt).toBeNull();
  });
});

describe("normalizeAlerts", () => {
  const raw = {
    JNC001: {
      TRIP001: {
        ambulanceId: "AMB001",
        severity: "critical",
        status: "preemption_active",
        message: "Approaching",
        updatedAt: NOW - 1000,
      },
      TRIP002: { ambulanceId: "AMB002", status: "completed", updatedAt: NOW - 90000 },
    },
  };

  it("flattens the nested junction/trip tree into keyed alerts", () => {
    const alerts = normalizeAlerts(raw, { kind: "police", meta: policeAlertMeta, nowMs: NOW });
    expect(alerts.map((a) => a.id)).toEqual(["JNC001:TRIP001", "JNC001:TRIP002"]);
    expect(alerts[0].kind).toBe("police");
    expect(alerts[0].groupId).toBe("JNC001");
    expect(alerts[0].tripId).toBe("TRIP001");
    expect(alerts[0].severity).toBe("Critical");
    expect(alerts[0].ageSeconds).toBe(1);
  });

  it("decorates each alert with its status label and tone", () => {
    const alerts = normalizeAlerts(raw, { kind: "police", meta: policeAlertMeta, nowMs: NOW });
    expect(alerts[0].label).toBe("Preemption active");
    expect(alerts[0].tone).toBe("danger");
    expect(alerts[1].label).toBe("Completed");
    expect(alerts[1].tone).toBe("success");
  });

  it("falls back to a neutral label for unrecognised statuses", () => {
    const alerts = normalizeAlerts(
      { HOSP001: { TRIP009: { status: "teleporting" } } },
      { kind: "hospital", meta: hospitalAlertMeta, nowMs: NOW },
    );
    expect(alerts[0].label).toBe("Teleporting");
    expect(alerts[0].tone).toBe("neutral");
    expect(alerts[0].status).toBe("teleporting");
  });

  it("survives empty and malformed nodes", () => {
    expect(normalizeAlerts(null, { kind: "police", meta: policeAlertMeta })).toEqual([]);
    expect(normalizeAlerts({ JNC001: null }, { kind: "police", meta: policeAlertMeta })).toEqual([]);
    expect(
      normalizeAlerts({ JNC001: { TRIP001: null } }, { kind: "police", meta: policeAlertMeta }),
    ).toEqual([]);
  });
});

describe("normalizeJunctionEvents", () => {
  it("namespaces Firebase rows and sorts newest first", () => {
    const events = normalizeJunctionEvents({
      "evt-1": { eventType: "gps_preempt_started", timestamp: 1000, junctionId: "JNC001" },
      "evt-2": { eventType: "rfid_clearance", timestamp: 2000, junctionId: "JNC001" },
    });
    expect(events.map((e) => e.id)).toEqual(["fb:evt-2", "fb:evt-1"]);
    expect(events[0].eventType).toBe("rfid_clearance");
    expect(events[0].source).toBe("firebase");
  });

  it("tolerates a missing timestamp", () => {
    const [event] = normalizeJunctionEvents({ "evt-1": { eventType: "approach_tracking" } });
    expect(typeof event.timestamp).toBe("number");
    expect(event.eventType).toBe("approach_tracking");
  });
});

describe("normalizeMqttEvent", () => {
  it("namespaces the MQTT id so it cannot collide with a Firebase row", () => {
    const event = normalizeMqttEvent({ junctionId: "JNC001", eventType: "rfid_clearance", timestamp: 1000 });
    expect(event.id).toBe("mqtt:JNC001:rfid_clearance:1000");
    expect(event.source).toBe("mqtt");
    expect(event.timestamp).toBe(1000);
  });

  it("defaults the event type and unknown junction", () => {
    const event = normalizeMqttEvent({});
    expect(event.eventType).toBe("system");
    expect(event.id.startsWith("mqtt:?")).toBe(true);
  });

  it("rejects payloads that are not objects", () => {
    expect(normalizeMqttEvent(null)).toBeNull();
    expect(normalizeMqttEvent(undefined)).toBeNull();
    expect(normalizeMqttEvent("hello")).toBeNull();
  });
});

describe("event labels", () => {
  it("covers the firmware event types", () => {
    expect(eventLabel("gps_preempt_started")).toBe("GPS preemption");
    expect(eventLabel("rssi_preempt_started")).toBe("RSSI fallback preemption");
    expect(eventLabel("rfid_clearance")).toBe("RFID clearance");
    expect(eventLabel("timeout_restore")).toBe("Safety timeout");
    expect(eventLabel("manual_reset")).toBe("Manual reset");
    expect(eventTone("rfid_clearance")).toBe("success");
    expect(eventTone("gps_preempt_started")).toBe("danger");
  });

  it("still understands the legacy entry/exit names", () => {
    expect(eventLabel("entry")).toBe("Corridor entry");
    expect(eventLabel("exit")).toBe("Corridor exit");
    expect(eventTone("entry")).toBe("danger");
    expect(eventTone("exit")).toBe("success");
  });

  it("humanises anything unrecognised", () => {
    expect(eventLabel("some_new_event")).toBe("Some New Event");
    expect(eventTone("some_new_event")).toBe("neutral");
    expect(eventLabel(null)).toBe("Event");
  });
});

describe("deriveCorridor", () => {
  const junctions = [
    { junctionId: "JNC001", location: { lat: 12.9716, lng: 77.5946 } },
    { junctionId: "JNC002", location: { lat: 12.9812, lng: 77.6121 } },
    { junctionId: "JNC003", location: null },
  ];
  const hospitals = [
    { hospitalId: "HOSP001", name: "City Care Hospital", location: { lat: 12.9698, lng: 77.6015 } },
  ];
  const ambulance = {
    ambulanceId: "AMB001",
    destinationHospitalId: "HOSP001",
    lastLocation: { lat: 12.9724, lng: 77.5930 },
    telemetry: { speedKmph: 46, headingDeg: 120 },
  };

  it("picks the nearest junction and measures the remaining hospital run", () => {
    const corridor = deriveCorridor({ ambulance, junctions, hospitals });
    expect(corridor.position).toEqual({ lat: 12.9724, lng: 77.5930 });
    expect(corridor.destination.name).toBe("City Care Hospital");
    expect(corridor.nearestJunction.junctionId).toBe("JNC001");
    expect(corridor.nearestJunctionMeters).toBeGreaterThan(100);
    expect(corridor.nearestJunctionMeters).toBeLessThan(250);
    expect(corridor.hospitalMeters).toBeGreaterThan(900);
    expect(corridor.hospitalMeters).toBeLessThan(1050);
    expect(corridor.hospitalEta).toBeCloseTo(etaSeconds(corridor.hospitalMeters, 46), 6);
    expect(corridor.speed).toBe(46);
    expect(corridor.headingDeg).toBe(120);
  });

  it("ignores junctions with no coordinates", () => {
    const corridor = deriveCorridor({ ambulance, junctions, hospitals });
    expect(corridor.nearestJunction.junctionId).not.toBe("JNC003");
  });

  it("falls back to the LoRa telemetry position when GPS is absent", () => {
    const viaTelemetry = deriveCorridor({
      ambulance: {
        ambulanceId: "AMB002",
        destinationHospitalId: "HOSP001",
        lastLocation: null,
        telemetry: { lat: 12.9724, lng: 77.5930, speedKmph: null },
      },
      junctions,
      hospitals,
    });
    expect(viaTelemetry.position).toEqual({ lat: 12.9724, lng: 77.5930 });
    expect(viaTelemetry.nearestJunction.junctionId).toBe("JNC001");
    // No live speed, so the ETA uses the conservative fallback speed.
    expect(viaTelemetry.hospitalEta).toBeCloseTo(etaSeconds(viaTelemetry.hospitalMeters, null), 6);
  });

  it("reports nothing measurable when there is no position at all", () => {
    const blind = deriveCorridor({
      ambulance: {
        ambulanceId: "AMB003",
        destinationHospitalId: "HOSP001",
        lastLocation: null,
        telemetry: { lat: null, lng: null, speedKmph: 40 },
      },
      junctions,
      hospitals,
    });
    expect(blind.position).toBeNull();
    expect(blind.nearestJunction).toBeNull();
    expect(blind.nearestJunctionMeters).toBeNull();
    expect(blind.hospitalMeters).toBeNull();
    expect(blind.hospitalEta).toBeNull();
    expect(blind.destination.name).toBe("City Care Hospital");
  });

  it("returns no destination when the hospital id is unknown", () => {
    const corridor = deriveCorridor({
      ambulance: { ...ambulance, destinationHospitalId: "HOSP999" },
      junctions,
      hospitals,
    });
    expect(corridor.destination).toBeNull();
    expect(corridor.hospitalMeters).toBeNull();
  });
});
