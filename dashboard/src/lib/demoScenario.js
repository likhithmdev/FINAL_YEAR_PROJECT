// Offline demo scenario. Produces a Firebase-shaped snapshot that the same
// normalisation pipeline consumes, then advances the ambulance along a route
// so the console can be demonstrated end-to-end without ESP32 hardware.
import { bearingDeg, haversineMeters } from "./geo";

export const ROUTE = [
  { lat: 12.9772, lng: 77.5875 },
  { lat: 12.9735, lng: 77.5920 },
  { lat: 12.9716, lng: 77.5946 }, // JNC001 stop line
  { lat: 12.9705, lng: 77.5982 },
  { lat: 12.9698, lng: 77.6015 }, // HOSP001 bay
];

const JUNCTION_POINT = { lat: 12.9716, lng: 77.5946 };
const SPEED_KMPH = 46;

function segmentLengths(points) {
  const lengths = [];
  for (let i = 0; i < points.length - 1; i += 1) {
    lengths.push(haversineMeters(points[i], points[i + 1]) || 0);
  }
  return lengths;
}

function pointAtDistance(points, lengths, distance) {
  let remaining = distance;
  for (let i = 0; i < lengths.length; i += 1) {
    if (remaining <= lengths[i]) {
      const t = lengths[i] === 0 ? 0 : remaining / lengths[i];
      const from = points[i];
      const to = points[i + 1];
      return {
        lat: from.lat + (to.lat - from.lat) * t,
        lng: from.lng + (to.lng - from.lng) * t,
        heading: bearingDeg(from, to),
      };
    }
    remaining -= lengths[i];
  }
  const last = points[points.length - 1];
  const prev = points[points.length - 2];
  return { lat: last.lat, lng: last.lng, heading: bearingDeg(prev, last) };
}

export function buildDemoWorld() {
  const lengths = segmentLengths(ROUTE);
  const totalMeters = lengths.reduce((sum, l) => sum + l, 0);
  // Start the run already on the approach so junction preemption is visible
  // the moment the console opens.
  const distanceToJunction = lengths[0] + lengths[1];
  return {
    lengths,
    totalMeters,
    distance: Math.max(0, distanceToJunction - 470),
    cleared: false,
    events: [
      {
        id: "evt-preempt",
        junctionId: "JNC001",
        junctionName: "Main Road Junction",
        ambulanceId: "AMB001",
        eventType: "gps_preempt_started",
        preemptionMode: "gps_lora",
        distanceMeters: 486,
        rssi: -71,
        timestamp: Date.now() - 42000,
      },
      {
        id: "evt-approach",
        junctionId: "JNC001",
        junctionName: "Main Road Junction",
        ambulanceId: "AMB001",
        eventType: "approach_tracking",
        preemptionMode: "gps_lora",
        distanceMeters: 640,
        rssi: -74,
        timestamp: Date.now() - 51000,
      },
    ],
  };
}

// Advances the demo by dtMs and returns the Firebase-shaped snapshot.
export function advanceDemo(world, dtMs) {
  const meters = (SPEED_KMPH * 1000 / 3600) * (dtMs / 1000);
  const startDistance = Math.max(0, world.lengths[0] + world.lengths[1] - 470);
  let distance = world.distance + meters;
  let wrapped = false;
  if (distance >= world.totalMeters) {
    // Loop the run so a live demo always shows an active corridor.
    distance = startDistance;
    wrapped = true;
  }
  const position = pointAtDistance(ROUTE, world.lengths, distance);
  const distToJunction = haversineMeters(position, JUNCTION_POINT);
  const crossed = distance >= (world.lengths[0] + world.lengths[1]);
  const approaching = !crossed && distToJunction != null && distToJunction <= 500;
  const events = world.events;

  if (crossed && !world.cleared) {
    events.push({
      id: `evt-clearance-${Date.now()}-${Math.round(distance)}`,
      junctionId: "JNC001",
      junctionName: "Main Road Junction",
      ambulanceId: "AMB001",
      eventType: "rfid_clearance",
      rfidTagId: "RFID_TAG_001",
      preemptionMode: "rfid_clearance",
      distanceMeters: Math.round(distToJunction || 0),
      timestamp: Date.now(),
    });
  }

  const wasCleared = wrapped ? false : world.cleared;
  const world2 = { ...world, distance, cleared: wasCleared || crossed, events };

  const signalState = approaching ? "priority_active" : "normal";
  const preemptionMode = approaching ? "gps_lora" : "none";
  const now = Date.now();

  return {
    world: world2,
    data: {
      ambulances: {
        AMB001: {
          ambulanceId: "AMB001",
          driverId: "DRV001",
          rfidTagId: "RFID_TAG_001",
          loraNodeId: "LORA_AMB001",
          status: "emergency_active",
          emergencyActive: true,
          severity: "Critical",
          destinationHospitalId: "HOSP001",
          lastLocation: {
            lat: position.lat,
            lng: position.lng,
            source: "android_gps",
            updatedAt: now,
          },
          lastLoRaTelemetry: {
            junctionId: "JNC001",
            lat: position.lat,
            lng: position.lng,
            speedKmph: SPEED_KMPH,
            headingDeg: Math.round(position.heading || 0),
            gpsFix: true,
            rssi: approaching ? -66 : -82,
            distanceMeters: Math.round(distToJunction || 0),
            bearingToJunctionDeg: Math.round(bearingDeg(position, JUNCTION_POINT) || 0),
            approaching,
            preemptionEligible: approaching,
            source: approaching ? "gps_lora" : "gps_lora",
            updatedAt: now,
          },
          updatedAt: now,
        },
      },
      emergencyTrips: {
        TRIP001: {
          tripId: "TRIP001",
          ambulanceId: "AMB001",
          driverId: "DRV001",
          destinationHospitalId: "HOSP001",
          destinationHospitalName: "City Care Hospital",
          severity: "Critical",
          status: "active",
          startedAt: now - 42000,
        },
      },
      junctions: {
        JNC001: {
          junctionId: "JNC001",
          name: "Main Road Junction",
          activeLane: "northbound",
          signalState,
          preemptionMode,
          activeAmbulanceId: approaching ? "AMB001" : null,
          distanceMeters: Math.round(distToJunction || 0),
          rssi: -66,
          lastDwellTime: crossed ? "11s" : null,
          approachThresholdMeters: 500,
          updatedAt: now,
        },
        JNC002: {
          junctionId: "JNC002",
          name: "Hospital Cross",
          activeLane: "eastbound",
          signalState: "normal",
          preemptionMode: "none",
          updatedAt: now,
        },
        JNC003: {
          junctionId: "JNC003",
          name: "Emergency Gate",
          activeLane: "southbound",
          signalState: "normal",
          preemptionMode: "none",
          updatedAt: now - 900000,
        },
      },
      hospitals: {
        HOSP001: { hospitalId: "HOSP001", name: "City Care Hospital", bedsAvailable: 8, emergencyAvailable: true, latitude: 12.9698, longitude: 77.6015, phone: "+91 80 4000 1000" },
        HOSP002: { hospitalId: "HOSP002", name: "Metro Emergency Center", bedsAvailable: 3, emergencyAvailable: true, latitude: 12.9812, longitude: 77.6121, phone: "+91 80 4000 2000" },
        HOSP003: { hospitalId: "HOSP003", name: "St. Mark Trauma Unit", bedsAvailable: 11, emergencyAvailable: false, latitude: 12.9601, longitude: 77.6178, phone: "+91 80 4000 3000" },
      },
      junctionEvents: Object.fromEntries(events.map((event) => [event.id, event])),
      policeAlerts: {
        JNC001: {
          TRIP001: {
            tripId: "TRIP001",
            ambulanceId: "AMB001",
            severity: "Critical",
            destinationHospitalId: "HOSP001",
            status: approaching ? "preemption_active" : "clearance",
            message: "Ambulance approaching junction through GPS-LoRa preemption",
            preemptionMode: approaching ? "gps_lora" : "rfid_clearance",
            distanceMeters: Math.round(distToJunction || 0),
            updatedAt: now,
          },
        },
      },
      hospitalAlerts: {
        HOSP001: {
          TRIP001: {
            tripId: "TRIP001",
            ambulanceId: "AMB001",
            severity: "Critical",
            status: "incoming",
            eta: "4 min",
            message: "Ambulance incoming to City Care Hospital",
            bayReadiness: { responseTeam: true, bedBay: true, doctorOnDuty: false, patientReceived: false },
            updatedAt: now,
          },
        },
      },
      loraTelemetry: {
        JNC001: {
          AMB001: {
            ambulanceId: "AMB001",
            tripId: "TRIP001",
            lat: position.lat,
            lng: position.lng,
            speedKmph: SPEED_KMPH,
            headingDeg: Math.round(position.heading || 0),
            gpsFix: true,
            rssi: approaching ? -66 : -82,
            distanceMeters: Math.round(distToJunction || 0),
            bearingToJunctionDeg: Math.round(bearingDeg(position, JUNCTION_POINT) || 0),
            approaching,
            preemptionEligible: approaching,
            updatedAt: now,
          },
        },
      },
    },
  };
}
