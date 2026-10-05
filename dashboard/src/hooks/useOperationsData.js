import { useEffect, useMemo, useState } from "react";
import { subscribeToDashboardData } from "../integrations/firebaseClient";
import {
  deriveCorridor,
  hospitalAlertMeta,
  normalizeAlerts,
  normalizeAmbulances,
  normalizeHospitals,
  normalizeJunctionEvents,
  normalizeJunctions,
  normalizeMqttEvent,
  normalizeTrips,
  policeAlertMeta,
} from "../lib/model";
import { advanceDemo, buildDemoWorld } from "../lib/demoScenario";

const MAX_EVENTS = 60;

function emptyOverlay() {
  return { signals: {}, approaches: {}, telemetry: {}, ambulanceStatus: {}, events: [] };
}

// Fold ephemeral MQTT messages on top of the durable Firebase snapshot. Only
// the latest MQTT value per key is kept, which is exactly the semantics the
// retained-style signals topics provide.
function mergeOverlay(base, overlay) {
  const raw = { ...base };
  const now = Date.now();

  const junctions = { ...(base.junctions || {}) };
  Object.entries(overlay.signals).forEach(([id, signal]) => {
    junctions[id] = { ...(junctions[id] || {}), ...signal };
  });
  Object.entries(overlay.approaches).forEach(([id, approach]) => {
    junctions[id] = {
      ...(junctions[id] || {}),
      distanceMeters: approach.distanceMeters ?? junctions[id]?.distanceMeters,
      rssi: approach.rssi ?? junctions[id]?.rssi,
      updatedAt: now,
    };
  });
  raw.junctions = junctions;

  const telemetry = { ...(base.loraTelemetry || {}) };
  const ambulances = { ...(base.ambulances || {}) };
  Object.entries(overlay.telemetry).forEach(([ambulanceId, payload]) => {
    const junctionId = payload.junctionId || "JNC001";
    telemetry[junctionId] = { ...(telemetry[junctionId] || {}), [ambulanceId]: payload };
    const existing = ambulances[ambulanceId] || { ambulanceId };
    ambulances[ambulanceId] = {
      ...existing,
      lastLoRaTelemetry: { ...(existing.lastLoRaTelemetry || {}), ...payload },
      lastLocation:
        payload.lat != null && payload.lng != null
          ? { lat: payload.lat, lng: payload.lng, source: "LoRa MQTT", updatedAt: now }
          : existing.lastLocation,
      updatedAt: now,
    };
  });
  Object.entries(overlay.ambulanceStatus).forEach(([ambulanceId, status]) => {
    ambulances[ambulanceId] = { ...(ambulances[ambulanceId] || { ambulanceId }), ...status };
  });
  raw.loraTelemetry = telemetry;
  raw.ambulances = ambulances;

  return raw;
}

function mergeEvents(...lists) {
  const seen = new Set();
  return lists
    .flat()
    .filter((event) => {
      if (!event || seen.has(event.id)) return false;
      seen.add(event.id);
      return true;
    })
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, MAX_EVENTS);
}

export function useOperationsData() {
  const [firebaseData, setFirebaseData] = useState({});
  const [firebaseError, setFirebaseError] = useState(null);
  const [firebaseReady, setFirebaseReady] = useState(false);
  const [mqttStatus, setMqttStatus] = useState("connecting");
  const [mqttError, setMqttError] = useState(null);
  const [lastMessageAt, setLastMessageAt] = useState(null);
  const [overlay, setOverlay] = useState(emptyOverlay);
  // `?demo=1` lets a presenter boot straight into the simulated scenario.
  const [demoMode, setDemoMode] = useState(
    () => typeof window !== "undefined" && new URLSearchParams(window.location.search).get("demo") === "1",
  );
  const [demoState, setDemoState] = useState(null);
  const [now, setNow] = useState(() => Date.now());

  // Firebase Realtime Database — the durable source of truth. Skipped in demo
  // mode, which is meant to run with no account and no network; without this
  // an unauthenticated demo session would collect spurious permission errors.
  useEffect(() => {
    if (demoMode) {
      setFirebaseReady(true);
      return undefined;
    }
    const unsubscribe = subscribeToDashboardData(
      (data) => {
        setFirebaseData(data);
        setFirebaseReady(true);
        setFirebaseError(null);
      },
      (error) => {
        setFirebaseError(error);
        setFirebaseReady(true);
      },
    );
    return unsubscribe;
  }, [demoMode]);

  // MQTT — low-latency device telemetry and events. The client is the single
  // heaviest dependency in the console, so it is imported dynamically and
  // arrives after the operator has already seen the first paint.
  useEffect(() => {
    if (demoMode) {
      setMqttStatus("demo");
      return undefined;
    }

    let cancelled = false;
    let unsubscribe = () => {};

    const pushEvent = (payload) => {
      const event = normalizeMqttEvent(payload);
      if (!event) return;
      setOverlay((current) => {
        if (current.events.some((existing) => existing.id === event.id)) return current;
        return { ...current, events: [event, ...current.events].slice(0, MAX_EVENTS) };
      });
      setLastMessageAt(Date.now());
    };

    const handlers = {
      onStatus: (status, message) => {
        setMqttStatus(status);
        if (status === "error") setMqttError(message || "MQTT error");
      },
      onEvent: pushEvent,
      onTripEvent: pushEvent,
      onSignal: (data) => {
        setOverlay((current) => ({
          ...current,
          signals: { ...current.signals, [data.junctionId]: data },
        }));
        setLastMessageAt(Date.now());
      },
      onApproach: (data) => {
        setOverlay((current) => ({
          ...current,
          approaches: { ...current.approaches, [data.junctionId]: data },
        }));
        setLastMessageAt(Date.now());
      },
      onTelemetry: (data) => {
        setOverlay((current) => ({
          ...current,
          telemetry: { ...current.telemetry, [data.ambulanceId]: data },
        }));
        setLastMessageAt(Date.now());
      },
      onAmbulanceStatus: (data) => {
        setOverlay((current) => ({
          ...current,
          ambulanceStatus: { ...current.ambulanceStatus, [data.ambulanceId]: data },
        }));
        setLastMessageAt(Date.now());
      },
    };

    import("../integrations/mqttClient")
      .then(({ subscribeToMqtt }) => {
        // The effect may have been torn down while the chunk was in flight.
        if (cancelled) return;
        unsubscribe = subscribeToMqtt(handlers);
      })
      .catch((error) => {
        setMqttStatus("error");
        setMqttError(error?.message || "Could not load the MQTT client");
      });

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [demoMode]);

  // Demo scenario engine — advances a simulated emergency once per second.
  useEffect(() => {
    if (!demoMode) {
      setDemoState(null);
      return undefined;
    }
    let world = buildDemoWorld();
    let last = Date.now();
    const tick = () => {
      const current = Date.now();
      const { world: nextWorld, data } = advanceDemo(world, current - last);
      world = nextWorld;
      last = current;
      setDemoState({ world: nextWorld, data });
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [demoMode]);

  // Drives relative timestamps ("updated 4s ago") without external updates.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const model = useMemo(() => {
    const usingDemo = demoMode && demoState;
    const raw = usingDemo ? demoState.data : mergeOverlay(firebaseData, overlay);

    const junctions = normalizeJunctions(raw.junctions, { nowMs: now });
    const hospitals = normalizeHospitals(raw.hospitals);
    const ambulances = normalizeAmbulances(raw.ambulances, { nowMs: now });
    const trips = normalizeTrips(raw.emergencyTrips);
    const events = mergeEvents(
      normalizeJunctionEvents(raw.junctionEvents),
      usingDemo ? [] : overlay.events,
    );
    const policeAlerts = normalizeAlerts(raw.policeAlerts, {
      kind: "police",
      meta: policeAlertMeta,
      nowMs: now,
    });
    const hospitalAlerts = normalizeAlerts(raw.hospitalAlerts, {
      kind: "hospital",
      meta: hospitalAlertMeta,
      nowMs: now,
    });

    const activeAmbulances = ambulances.filter((ambulance) => ambulance.emergencyActive);
    const corridors = activeAmbulances.map((ambulance) => ({
      ambulance,
      ...deriveCorridor({ ambulance, junctions, hospitals }),
    }));

    const freshest = [...ambulances, ...junctions]
      .map((entity) => entity.updatedAt)
      .filter((value) => typeof value === "number");
    const lastDataAt = freshest.length ? Math.max(...freshest) : null;

    return {
      source: usingDemo ? "demo" : "live",
      junctions,
      hospitals,
      ambulances,
      trips,
      events,
      policeAlerts,
      hospitalAlerts,
      corridors,
      activeAmbulances,
      activeTrips: trips.filter((trip) => trip.status === "active"),
      lastDataAt,
    };
  }, [firebaseData, overlay, demoMode, demoState, now]);

  return {
    ...model,
    now,
    firebase: { ready: firebaseReady, error: firebaseError, online: firebaseReady && !firebaseError },
    mqtt: { status: mqttStatus, error: mqttError, lastMessageAt },
    demo: { mode: demoMode, setMode: setDemoMode, world: demoState?.world || null },
  };
}
