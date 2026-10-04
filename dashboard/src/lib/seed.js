// Optional seeding of demo records straight from the control room. Only runs
// when the operator explicitly clicks a seed button in the System tab.
import { firebasePaths, writeNode } from "../integrations/firebaseClient";

export const DEMO_HOSPITALS = {
  HOSP001: {
    hospitalId: "HOSP001",
    name: "City Care Hospital",
    bedsAvailable: 8,
    emergencyAvailable: true,
    latitude: 12.9698,
    longitude: 77.6015,
    phone: "+91 80 4000 1000",
    eta: "6 min",
    distance: "2.4 km",
  },
  HOSP002: {
    hospitalId: "HOSP002",
    name: "Metro Emergency Center",
    bedsAvailable: 3,
    emergencyAvailable: true,
    latitude: 12.9812,
    longitude: 77.6121,
    phone: "+91 80 4000 2000",
    eta: "9 min",
    distance: "3.1 km",
  },
  HOSP003: {
    hospitalId: "HOSP003",
    name: "St. Mark Trauma Unit",
    bedsAvailable: 11,
    emergencyAvailable: true,
    latitude: 12.9601,
    longitude: 77.6178,
    phone: "+91 80 4000 3000",
    eta: "12 min",
    distance: "4.6 km",
  },
};

const DEMO_USERS = {
  driver_001: { userId: "driver_001", name: "Driver One", pin: "1111", role: "ambulance_driver", ambulanceId: "AMB001", active: true },
  police_001: { userId: "police_001", name: "Traffic Police", pin: "2222", role: "police", assignedJunctionId: "JNC001", active: true },
  hospital_001: { userId: "hospital_001", name: "City Care Desk", pin: "3333", role: "hospital", hospitalId: "HOSP001", active: true },
  admin_001: { userId: "admin_001", name: "System Admin", pin: "0000", role: "admin", active: true },
};

const DEMO_JUNCTIONS = {
  JNC001: {
    junctionId: "JNC001",
    name: "Main Road Junction",
    activeLane: "northbound",
    signalState: "normal",
    preemptionMode: "none",
    approachThresholdMeters: 500,
    bearingToleranceDeg: 35,
    rssiFallbackThresholdDbm: -65,
    rssiConsecutivePacketCount: 3,
    gpsPacketTimeoutMs: 5000,
    clearanceTimeoutMs: 90000,
    latitude: 12.9716,
    longitude: 77.5946,
  },
  JNC002: {
    junctionId: "JNC002",
    name: "Hospital Cross",
    activeLane: "eastbound",
    signalState: "normal",
    preemptionMode: "none",
    approachThresholdMeters: 500,
    bearingToleranceDeg: 35,
    rssiFallbackThresholdDbm: -65,
    gpsPacketTimeoutMs: 5000,
    clearanceTimeoutMs: 90000,
    latitude: 12.9752,
    longitude: 77.6001,
  },
  JNC003: {
    junctionId: "JNC003",
    name: "Emergency Gate",
    activeLane: "southbound",
    signalState: "normal",
    preemptionMode: "none",
    approachThresholdMeters: 500,
    bearingToleranceDeg: 35,
    rssiFallbackThresholdDbm: -65,
    gpsPacketTimeoutMs: 5000,
    clearanceTimeoutMs: 90000,
    latitude: 12.9668,
    longitude: 77.6072,
  },
};

export function seedDemoHospitals() {
  return writeNode(firebasePaths.hospitals, DEMO_HOSPITALS);
}

export function seedDemoInfrastructure() {
  const now = Date.now();
  return Promise.all([
    writeNode(firebasePaths.users, DEMO_USERS),
    writeNode(firebasePaths.hospitals, DEMO_HOSPITALS),
    writeNode(firebasePaths.junctions, DEMO_JUNCTIONS),
    writeNode(firebasePaths.ambulances, {
      AMB001: {
        ambulanceId: "AMB001",
        driverId: "DRV001",
        rfidTagId: "RFID_TAG_001",
        loraNodeId: "LORA_AMB001",
        status: "available",
        emergencyActive: false,
        updatedAt: now,
      },
    }),
    writeNode(firebasePaths.rfidTags, {
      RFID_TAG_001: { rfidTagId: "RFID_TAG_001", ambulanceId: "AMB001", authorized: true, active: true, updatedAt: now },
    }),
  ]);
}
