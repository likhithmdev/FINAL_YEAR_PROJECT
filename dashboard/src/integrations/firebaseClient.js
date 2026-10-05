import { initializeApp } from "firebase/app";
import { getDatabase, onValue, push, ref, set, update } from "firebase/database";
import {
  browserSessionPersistence,
  getAuth,
  onAuthStateChanged,
  setPersistence,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";

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
export const auth = getAuth(firebaseApp);

// --------------------------------------------------------------- auth gate --
// The Realtime Database rules require `auth != null`, so nothing is readable
// until an operator signs in. Every subscribe/write helper below therefore
// assumes an authenticated session.

// Session-scoped on purpose: a control-room workstation is shared, so closing
// the browser should end the session rather than leave the console open.
let persistenceReady;
function ensurePersistence() {
  if (!persistenceReady) {
    persistenceReady = setPersistence(auth, browserSessionPersistence).catch(() => {
      /* Non-fatal: fall back to the SDK default (local) persistence. */
    });
  }
  return persistenceReady;
}

// Turns Firebase's error codes into something an operator can act on. The
// provider-not-enabled case is the most likely first-run failure and its raw
// code is opaque.
export function describeAuthError(error) {
  const code = error?.code || "";
  switch (code) {
    case "auth/invalid-email":
      return "That email address is not valid.";
    case "auth/missing-password":
      return "Enter a password.";
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Wrong email or password.";
    case "auth/user-disabled":
      return "This account has been disabled.";
    case "auth/too-many-requests":
      return "Too many attempts. Wait a moment and try again.";
    case "auth/operation-not-allowed":
    case "auth/configuration-not-found":
      return "Email/password sign-in is not enabled for this Firebase project. Enable it under Authentication > Sign-in method.";
    case "auth/network-request-failed":
      return "Cannot reach Firebase. Check the network connection.";
    default:
      return error?.message || "Sign-in failed.";
  }
}

export async function signInOperator(email, password) {
  await ensurePersistence();
  return signInWithEmailAndPassword(auth, email, password);
}

export function signOutOperator() {
  return signOut(auth);
}

// Reports the current operator. `ready` stays false until the SDK has restored
// any existing session, so the UI does not flash the login screen on reload.
export function subscribeToAuth(onChange) {
  return onAuthStateChanged(
    auth,
    (user) => onChange({ ready: true, user }),
    (error) => onChange({ ready: true, user: null, error }),
  );
}

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
