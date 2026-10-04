import { initializeApp } from "firebase/app";
import { getDatabase, onValue, push, ref, set, update } from "firebase/database";

export const firebaseConfig = {
  apiKey: "AIzaSyCqg4gsohXZZB3wBEeAKR1wND-vYTg9H70",
  authDomain: "smart-ambulance-36f9d.firebaseapp.com",
  databaseURL: "https://smart-ambulance-36f9d-default-rtdb.firebaseio.com",
  projectId: "smart-ambulance-36f9d",
  storageBucket: "smart-ambulance-36f9d.firebasestorage.app",
  messagingSenderId: "735414353984",
  appId: "1:735414353984:web:0401c5a04025560e4e9fa5",
};

// Single source of truth for every Realtime Database node. These mirror
// `FirebasePaths.kt` in the Android app so the dashboard, phone and firmware
// all agree on the schema.
export const firebasePaths = {
  users: "users",
  drivers: "drivers",
  ambulances: "ambulances",
  emergencyTrips: "emergencyTrips",
  junctions: "junctions",
  junctionEvents: "junctionEvents",
  loraTelemetry: "loraTelemetry",
  hospitals: "hospitals",
  hospitalAlerts: "hospitalAlerts",
  policeAlerts: "policeAlerts",
  rfidTags: "rfidTags",
};

export const firebaseApp = initializeApp(firebaseConfig);
export const database = getDatabase(firebaseApp);

// Subscribe to the whole control-room tree. onError receives an Error when
// rules/network refuse access so the UI can fall back gracefully instead of
// hanging on "connecting" forever.
export function subscribeToDashboardData(onData, onError) {
  try {
    const unsubscribe = onValue(
      ref(database),
      (snapshot) => onData(snapshot.val() || {}),
      (error) => onError?.(error instanceof Error ? error : new Error(String(error?.message || error))),
    );
    return unsubscribe;
  } catch (error) {
    onError?.(error);
    return () => {};
  }
}

// Seed the small demo hospital set only when the caller explicitly asks
// (never on page load, so the dashboard is safe to open in production).
// Generic whole-node write used by the seeding helpers.
export function writeNode(path, value) {
  return set(ref(database, path), value);
}

export function seedHospitals(hospitals) {
  const hospitalMap = Object.fromEntries(hospitals.map((hospital) => [hospital.id, hospital]));
  return set(ref(database, firebasePaths.hospitals), hospitalMap);
}

export function writeAmbulanceStatus(ambulanceId, data) {
  return update(ref(database, `${firebasePaths.ambulances}/${ambulanceId}`), data);
}

export function writeTrip(tripId, data) {
  return update(ref(database, `${firebasePaths.emergencyTrips}/${tripId}`), data);
}

export function writeJunction(junctionId, data) {
  return update(ref(database, `${firebasePaths.junctions}/${junctionId}`), data);
}

export function writeJunctionEvent(event) {
  return push(ref(database, firebasePaths.junctionEvents), event);
}

export function writeLoRaTelemetry(junctionId, ambulanceId, data) {
  return update(
    ref(database, `${firebasePaths.loraTelemetry}/${junctionId}/${ambulanceId}`),
    data,
  );
}

export function writePoliceAlert(junctionId, tripId, data) {
  return update(ref(database, `${firebasePaths.policeAlerts}/${junctionId}/${tripId}`), data);
}

export function writeHospitalAlert(hospitalId, tripId, data) {
  return update(ref(database, `${firebasePaths.hospitalAlerts}/${hospitalId}/${tripId}`), data);
}
