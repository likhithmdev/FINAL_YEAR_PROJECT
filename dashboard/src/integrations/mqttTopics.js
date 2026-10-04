// MQTT topic catalogue. Kept in sync with `MqttTopics.kt` (Android) and the
// firmware publishing code. Wildcard variants are used for subscriptions.

const PREFIX = "smart-ambulance";

export const mqttTopics = {
  prefix: PREFIX,
  ambulanceLoRaGps: (ambulanceId) => `${PREFIX}/ambulances/${ambulanceId}/lora-gps`,
  ambulanceStatus: (ambulanceId) => `${PREFIX}/ambulances/${ambulanceId}/status`,
  junctionApproach: (junctionId) => `${PREFIX}/junctions/${junctionId}/approach`,
  junctionEvents: (junctionId) => `${PREFIX}/junctions/${junctionId}/events`,
  junctionSignal: (junctionId) => `${PREFIX}/junctions/${junctionId}/signal`,
  tripEvents: (tripId) => `${PREFIX}/trips/${tripId}/events`,

  junctionEventsWildcard: `${PREFIX}/junctions/+/events`,
  junctionSignalWildcard: `${PREFIX}/junctions/+/signal`,
  junctionApproachWildcard: `${PREFIX}/junctions/+/approach`,
  ambulanceLoRaGpsWildcard: `${PREFIX}/ambulances/+/lora-gps`,
  ambulanceStatusWildcard: `${PREFIX}/ambulances/+/status`,
  tripEventsWildcard: `${PREFIX}/trips/+/events`,
};

export const mqttBrokerDefaults = {
  localWebSocketUrl: "ws://localhost:9001",
  publicTestWebSocketUrl: "wss://broker.hivemq.com:8884/mqtt",
};
