// Normalisation layer. Firebase stores objects keyed by id and different
// devices write slightly different field names (lat vs latitude, signalState
// vs preemptionEligible). Everything downstream consumes the shapes produced
// here so views never have to know about those quirks.
import { ageSeconds, timestampMs, titleCase } from "./format";
import { bearingDeg, haversineMeters, etaSeconds } from "./geo";

export const EVENT_TYPES = {
  lora_gps_packet: { label: "LoRa GPS packet", tone: "neutral" },
  approach_tracking: { label: "Approach tracking", tone: "neutral" },
  gps_preempt_started: { label: "GPS preemption", tone: "danger" },
  rssi_preempt_started: { label: "RSSI fallback preemption", tone: "amber" },
  rfid_clearance: { label: "RFID clearance", tone: "success" },
  timeout_restore: { label: "Safety timeout", tone: "amber" },
  manual_reset: { label: "Manual reset", tone: "info" },
  // Legacy event names still present in historical Realtime Database records.
  entry: { label: "Corridor entry", tone: "danger" },
  exit: { label: "Corridor exit", tone: "success" },
};

export const SEVERITY = {
  Critical: { label: "P1 · Critical", tone: "danger", rank: 1 },
  Serious: { label: "P2 · Serious", tone: "amber", rank: 2 },
  Moderate: { label: "P3 · Moderate", tone: "info", rank: 3 },
};

export function severityTone(severity) {
  return SEVERITY[titleCase(severity)]?.tone || "neutral";
}

export function severityLabel(severity) {
  const key = titleCase(severity);
  return SEVERITY[key]?.label || severity || "Unspecified";
}

// Junction coordinates are not always stored in Firebase. Keep a fallback
// catalogue for the demo corridor so markers land in a sensible place, while
// still letting database values win when present.
export const JUNCTION_FALLBACKS = {
  JNC001: { name: "Main Road Junction", lat: 12.9716, lng: 77.5946, lane: "Northbound" },
  JNC002: { name: "Hospital Cross", lat: 12.9752, lng: 77.6001, lane: "Eastbound" },
  JNC003: { name: "Emergency Gate", lat: 12.9668, lng: 77.6072, lane: "Southbound" },
};

export const HOSPITAL_FALLBACKS = {
  HOSP001: { name: "City Care Hospital", lat: 12.9698, lng: 77.6015 },
  HOSP002: { name: "Metro Emergency Center", lat: 12.9812, lng: 77.6121 },
  HOSP003: { name: "St. Mark Trauma Unit", lat: 12.9601, lng: 77.6178 },
};

function coordOf(source) {
  if (!source) return null;
  const lat = source.lat ?? source.latitude ?? source.location?.lat;
  const lng = source.lng ?? source.longitude ?? source.location?.lng;
  if (typeof lat !== "number" || typeof lng !== "number") return null;
  return { lat, lng };
}

export function toArray(raw) {
  if (!raw || typeof raw !== "object") return [];
  return Object.entries(raw).map(([id, value]) => ({
    id,
    ...(value && typeof value === "object" ? value : {}),
  }));
}

export function normalizeJunctions(raw, { nowMs = Date.now() } = {}) {
  const fromDb = toArray(raw);
  const ids = new Set([
    ...Object.keys(JUNCTION_FALLBACKS),
    ...fromDb.map((junction) => junction.id),
  ]);
  return Array.from(ids)
    .map((id) => {
      const fallback = JUNCTION_FALLBACKS[id] || {};
      const dbJunction = fromDb.find((junction) => junction.id === id) || {};
      const signalState = dbJunction.signalState || "normal";
      const updatedMs = timestampMs(dbJunction.updatedAt);
      const online =
        signalState === "priority_active" || dbJunction.esp32Online === true
          ? true
          : updatedMs != null
            ? (nowMs - updatedMs) / 1000 < 600
            : Boolean(JUNCTION_FALLBACKS[id]);
      return {
        junctionId: dbJunction.junctionId || id,
        name: dbJunction.name || fallback.name || id,
        lane: titleCase(dbJunction.activeLane || fallback.lane || ""),
        signalState,
        preemptionMode: dbJunction.preemptionMode || "none",
        activeAmbulanceId: dbJunction.activeAmbulanceId || null,
        distanceMeters: dbJunction.distanceMeters ?? null,
        rssi: dbJunction.rssi ?? null,
        lastDwellTime: dbJunction.lastDwellTime || null,
        approachThresholdMeters: dbJunction.approachThresholdMeters ?? 500,
        bearingToleranceDeg: dbJunction.bearingToleranceDeg ?? 35,
        rssiFallbackThresholdDbm: dbJunction.rssiFallbackThresholdDbm ?? -65,
        gpsPacketTimeoutMs: dbJunction.gpsPacketTimeoutMs ?? 5000,
        clearanceTimeoutMs: dbJunction.clearanceTimeoutMs ?? 90000,
        updatedAt: updatedMs,
        ageSeconds: ageSeconds(dbJunction.updatedAt, nowMs),
        online,
        location: coordOf(dbJunction) || (fallback.lat != null ? { lat: fallback.lat, lng: fallback.lng } : null),
        source: "firebase",
      };
    })
    .sort((a, b) => a.junctionId.localeCompare(b.junctionId));
}

export function normalizeHospitals(raw) {
  const fromDb = toArray(raw);
  const ids = new Set([...Object.keys(HOSPITAL_FALLBACKS), ...fromDb.map((h) => h.id)]);
  return Array.from(ids)
    .map((id) => {
      const fallback = HOSPITAL_FALLBACKS[id] || {};
      const dbHospital = fromDb.find((h) => h.id === id) || {};
      const coord = coordOf(dbHospital);
      return {
        hospitalId: dbHospital.hospitalId || id,
        name: dbHospital.name || fallback.name || id,
        bedsAvailable: dbHospital.bedsAvailable ?? null,
        emergencyAvailable: dbHospital.emergencyAvailable ?? true,
        phone: dbHospital.phone || dbHospital.contact || null,
        address: dbHospital.address || null,
        location: coord || (fallback.lat != null ? { lat: fallback.lat, lng: fallback.lng } : null),
        staticDistance: dbHospital.distance || null,
        staticEta: dbHospital.eta || null,
      };
    })
    .sort((a, b) => a.hospitalId.localeCompare(b.hospitalId));
}

export function normalizeAmbulances(raw, { nowMs = Date.now() } = {}) {
  return toArray(raw)
    .map((amb) => {
      const telemetry = amb.lastLoRaTelemetry || {};
      const lastLocation = coordOf(amb.lastLocation);
      return {
        ambulanceId: amb.ambulanceId || amb.id,
        driverId: amb.driverId || null,
        rfidTagId: amb.rfidTagId || null,
        loraNodeId: amb.loraNodeId || null,
        status: amb.status || "available",
        emergencyActive: Boolean(amb.emergencyActive || amb.status === "emergency_active"),
        severity: titleCase(amb.severity || ""),
        destinationHospitalId: amb.destinationHospitalId || null,
        lastLocation: lastLocation
          ? { ...lastLocation, source: amb.lastLocation.source || "unknown", updatedAt: timestampMs(amb.lastLocation.updatedAt) }
          : null,
        telemetry: {
          junctionId: telemetry.junctionId || null,
          lat: telemetry.lat ?? null,
          lng: telemetry.lng ?? null,
          speedKmph: telemetry.speedKmph ?? null,
          headingDeg: telemetry.headingDeg ?? null,
          gpsFix: telemetry.gpsFix ?? null,
          rssi: telemetry.rssi ?? null,
          distanceMeters: telemetry.distanceMeters ?? null,
          bearingToJunctionDeg: telemetry.bearingToJunctionDeg ?? null,
          approaching: telemetry.approaching ?? null,
          preemptionEligible: telemetry.preemptionEligible ?? null,
          source: telemetry.source || null,
          updatedAt: timestampMs(telemetry.updatedAt),
        },
        updatedAt: timestampMs(amb.updatedAt),
        ageSeconds: ageSeconds(amb.updatedAt, nowMs),
      };
    })
    .sort((a, b) => a.ambulanceId.localeCompare(b.ambulanceId));
}

export function normalizeTrips(raw) {
  return toArray(raw)
    .map((trip) => ({
      tripId: trip.tripId || trip.id,
      ambulanceId: trip.ambulanceId || null,
      driverId: trip.driverId || null,
      destinationHospitalId: trip.destinationHospitalId || null,
      destinationHospitalName: trip.destinationHospitalName || null,
      severity: titleCase(trip.severity || ""),
      status: trip.status || "unknown",
      startedAt: timestampMs(trip.startedAt),
      endedAt: timestampMs(trip.endedAt),
    }))
    .sort((a, b) => (b.startedAt || 0) - (a.startedAt || 0));
}

const POLICE_STATUS = {
  ambulance_approaching: { label: "Approaching", tone: "amber" },
  preemption_active: { label: "Preemption active", tone: "danger" },
  clearance: { label: "Clearing", tone: "info" },
  completed: { label: "Completed", tone: "success" },
};

const HOSPITAL_STATUS = {
  incoming: { label: "Incoming", tone: "danger" },
  team_alerted: { label: "Team alerted", tone: "amber" },
  bed_ready: { label: "Bed ready", tone: "amber" },
  doctor_ready: { label: "Doctor ready", tone: "amber" },
  completed: { label: "Handover complete", tone: "success" },
};

export function policeAlertMeta(status) {
  return POLICE_STATUS[status] || { label: titleCase(status || "alert"), tone: "neutral" };
}

export function hospitalAlertMeta(status) {
  return HOSPITAL_STATUS[status] || { label: titleCase(status || "alert"), tone: "neutral" };
}

// hospitalAlerts/{hospitalId}/{tripId} and policeAlerts/{junctionId}/{tripId}
export function normalizeAlerts(raw, { kind, meta, nowMs = Date.now() }) {
  const alerts = [];
  Object.entries(raw || {}).forEach(([groupKey, trips]) => {
    Object.entries(trips || {}).forEach(([tripId, alert]) => {
      if (!alert || typeof alert !== "object") return;
      alerts.push({
        id: `${groupKey}:${tripId}`,
        kind,
        groupId: groupKey,
        tripId,
        ambulanceId: alert.ambulanceId || null,
        severity: titleCase(alert.severity || ""),
        status: alert.status || "unknown",
        ...meta(alert.status),
        message: alert.message || null,
        eta: alert.eta || null,
        preemptionMode: alert.preemptionMode || null,
        distanceMeters: alert.distanceMeters ?? null,
        destinationHospitalId: alert.destinationHospitalId || null,
        bayReadiness: alert.bayReadiness || null,
        updatedAt: timestampMs(alert.updatedAt),
        ageSeconds: ageSeconds(alert.updatedAt, nowMs),
      });
    });
  });
  return alerts.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
}

export function normalizeJunctionEvents(raw) {
  return toArray(raw)
    .map((event) => ({
      id: `fb:${event.id}`,
      junctionId: event.junctionId || null,
      junctionName: event.junctionName || null,
      ambulanceId: event.ambulanceId || null,
      eventType: event.eventType || "system",
      rfidTagId: event.rfidTagId || null,
      preemptionMode: event.preemptionMode || null,
      distanceMeters: event.distanceMeters ?? null,
      rssi: event.rssi ?? null,
      source: "firebase",
      timestamp: timestampMs(event.timestamp) ?? Date.now(),
    }))
    .sort((a, b) => b.timestamp - a.timestamp);
}

export function normalizeMqttEvent(payload) {
  if (!payload || typeof payload !== "object") return null;
  const eventType = payload.eventType || "system";
  return {
    id: `mqtt:${payload.junctionId || "?"}:${eventType}:${payload.timestamp || Date.now()}`,
    junctionId: payload.junctionId || null,
    junctionName: payload.junctionName || null,
    ambulanceId: payload.ambulanceId || null,
    eventType,
    rfidTagId: payload.rfidTagId || null,
    preemptionMode: payload.preemptionMode || null,
    distanceMeters: payload.distanceMeters ?? null,
    rssi: payload.rssi ?? null,
    source: "mqtt",
    timestamp: timestampMs(payload.timestamp) ?? Date.now(),
  };
}

export function eventLabel(eventType) {
  return EVENT_TYPES[eventType]?.label || titleCase(eventType || "Event");
}

export function eventTone(eventType) {
  return EVENT_TYPES[eventType]?.tone || "neutral";
}

// Derive corridor metrics for one ambulance: distance/ETA to its destination
// hospital and the nearest junction it is approaching.
export function deriveCorridor({ ambulance, junctions, hospitals }) {
  const position = ambulance?.lastLocation || coordOf(ambulance?.telemetry) || null;
  const destination = hospitals.find((h) => h.hospitalId === ambulance?.destinationHospitalId) || null;
  const speed = ambulance?.telemetry?.speedKmph ?? null;

  const junctionDistances = junctions
    .filter((junction) => junction.location)
    .map((junction) => {
      const meters = position ? haversineMeters(position, junction.location) : null;
      const bearing = position ? bearingDeg(position, junction.location) : null;
      return { junction, meters, bearing };
    })
    .filter((entry) => entry.meters != null)
    .sort((a, b) => a.meters - b.meters);

  const nearest = junctionDistances[0] || null;
  const hospitalMeters = position && destination?.location ? haversineMeters(position, destination.location) : null;
  const hospitalEta = etaSeconds(hospitalMeters, speed);

  return {
    position,
    destination,
    nearestJunction: nearest?.junction || null,
    nearestJunctionMeters: nearest?.meters ?? null,
    nearestJunctionEta: etaSeconds(nearest?.meters ?? null, speed),
    hospitalMeters,
    hospitalEta,
    speed,
    headingDeg: ambulance?.telemetry?.headingDeg ?? null,
  };
}
