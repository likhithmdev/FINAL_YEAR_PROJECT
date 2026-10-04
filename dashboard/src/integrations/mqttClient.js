import mqtt from "mqtt";
import { mqttBrokerDefaults, mqttTopics } from "./mqttTopics";

// Every topic the control room listens to. Wildcards keep the subscriber
// agnostic to how many junctions/ambulances are deployed.
const SUBSCRIPTIONS = [
  mqttTopics.junctionEventsWildcard,
  mqttTopics.junctionSignalWildcard,
  mqttTopics.junctionApproachWildcard,
  mqttTopics.ambulanceLoRaGpsWildcard,
  mqttTopics.ambulanceStatusWildcard,
  mqttTopics.tripEventsWildcard,
];

function parsePayload(payload) {
  try {
    return JSON.parse(payload.toString());
  } catch {
    return null;
  }
}

function junctionIdFrom(topic, data) {
  return data.junctionId || topic.split("/")[2];
}

function ambulanceIdFrom(topic, data) {
  return data.ambulanceId || topic.split("/")[2];
}

// Connects to the broker and dispatches normalised messages to the caller.
// Returns an unsubscribe function. Status transitions are reported through
// onStatus so the UI can show a truthful connectivity indicator.
export function subscribeToMqtt({ onStatus, onEvent, onSignal, onApproach, onTelemetry, onAmbulanceStatus, onTripEvent }) {
  const url = import.meta.env.VITE_MQTT_URL || mqttBrokerDefaults.publicTestWebSocketUrl;
  let client;
  try {
    client = mqtt.connect(url, {
      clientId: `smart-ambulance-dash-${Math.random().toString(16).slice(2, 10)}`,
      clean: true,
      reconnectPeriod: 4000,
      connectTimeout: 8000,
      protocolVersion: 4,
    });
  } catch (error) {
    onStatus?.("error", error?.message);
    return () => {};
  }

  client.on("connect", () => {
    onStatus?.("live");
    SUBSCRIPTIONS.forEach((topic) => client.subscribe(topic));
  });
  client.on("reconnect", () => onStatus?.("connecting"));
  client.on("offline", () => onStatus?.("offline"));
  client.on("close", () => onStatus?.("offline"));
  client.on("error", (error) => onStatus?.("error", error?.message));

  client.on("message", (topic, payload) => {
    const data = parsePayload(payload);
    if (!data) return;

    // Route by topic prefix because junction and trip events share the
    // `/events` suffix.
    if (topic.startsWith(`${mqttTopics.prefix}/trips/`)) {
      if (topic.endsWith("/events")) onTripEvent?.(data, topic);
      return;
    }
    if (topic.startsWith(`${mqttTopics.prefix}/junctions/`)) {
      if (topic.endsWith("/events")) onEvent?.(data, topic);
      else if (topic.endsWith("/signal")) onSignal?.({ ...data, junctionId: junctionIdFrom(topic, data) }, topic);
      else if (topic.endsWith("/approach")) onApproach?.({ ...data, junctionId: junctionIdFrom(topic, data) }, topic);
      return;
    }
    if (topic.startsWith(`${mqttTopics.prefix}/ambulances/`)) {
      if (topic.endsWith("/lora-gps")) onTelemetry?.({ ...data, ambulanceId: ambulanceIdFrom(topic, data) }, topic);
      else if (topic.endsWith("/status")) onAmbulanceStatus?.({ ...data, ambulanceId: ambulanceIdFrom(topic, data) }, topic);
    }
  });

  return () => {
    try {
      client.end(true);
    } catch {
      /* already closed */
    }
  };
}
