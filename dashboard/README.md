# Operations Console (Web Dashboard)

React + Vite control-room console for the Smart Ambulance Priority Traffic Control
System. It is the traffic-police / control-room view of the same Firebase
Realtime Database and MQTT topics used by the Android app and the ESP32
firmware — nothing about the IoT contracts changed.

## Run locally

```bash
npm install
npm run dev        # http://localhost:5180
npm run build      # production bundle in dist/
```

### Demo mode

Append `?demo=1` (or flip **System → Run simulated emergency corridor**) to boot
a self-contained simulated emergency. The demo advances an ambulance along the
corridor once per second and loops, so the console can be demonstrated with no
ESP32 hardware and no Firebase data.

## Views

| View | Purpose |
| --- | --- |
| **Live ops** | Map of ambulances, junctions and hospitals, active-response corridor card with ETA/distance/speed, junction status, live event log. |
| **Junctions** | Per-junction signal state, preemption mode, LoRa RSSI, approach distance, firmware thresholds, plus control-room override and a tabletop hardware simulator. |
| **Ambulances** | Fleet status, LoRa telemetry health (RSSI, GPS fix, speed, approach), destination and last heartbeat. |
| **Hospitals** | Bed availability, inbound alerts, ETA and trauma-bay readiness. |
| **Alerts** | Unified junction + hospital alert stream with severity and response status. |
| **System** | Service health (Firebase, MQTT, LoRa links, RFID, GPS), data freshness, demo toggle and one-click seeding. |

The layout is responsive: a persistent sidebar on desktop and a bottom tab bar
on mobile.

## Data sources

- **Firebase Realtime Database** — durable state: `ambulances`, `junctions`,
  `emergencyTrips`, `junctionEvents`, `loraTelemetry`, `hospitals`,
  `policeAlerts`, `hospitalAlerts`, `rfidTags`. Paths are centralised in
  [`src/integrations/firebaseClient.js`](src/integrations/firebaseClient.js)
  and mirror `FirebasePaths.kt`.
- **MQTT** — low-latency telemetry/events. Topics live in
  [`src/integrations/mqttTopics.js`](src/integrations/mqttTopics.js) and mirror
  `MqttTopics.kt`. Override the broker with `VITE_MQTT_URL`.
  Default: `wss://broker.hivemq.com:8884/mqtt`.

Ephemeral MQTT values are merged on top of the durable Firebase snapshot per
key, so the console always shows the freshest value from either source.

## Code layout

```text
src/
├── App.jsx                     # shell, navigation, control-room actions
├── main.jsx                    # entry point
├── views/                      # one module per console view
├── components/                 # MapPanel, EventFeed, shared UI primitives
├── hooks/useOperationsData.js  # merges Firebase + MQTT + demo into one model
├── integrations/               # firebaseClient, mqttClient, mqttTopics
└── lib/                        # geo, formatting, model normalisers, demo, seed
```

The map uses key-free OpenStreetMap raster tiles with a CSS dark filter, so it
renders on an air-gapped demo laptop without any map API key.

## Notes

- The dashboard never writes to Firebase on page load. Seeding is explicit
  (**System → Demo & seeding**) and only runs on a button press.
- Junction overrides and the tabletop simulator write standard junction /
  telemetry / event records, so they behave exactly like firmware traffic and
  are picked up by the Android app and other clients.
