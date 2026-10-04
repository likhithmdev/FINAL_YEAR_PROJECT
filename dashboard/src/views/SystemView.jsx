import React from "react";
import {
  Activity,
  Database,
  FlaskConical,
  Radio,
  Satellite,
  ServerCog,
  ShieldCheck,
  Tag,
  Wifi,
} from "lucide-react";
import { Badge, Dot, KeyValue, Panel, Toggle } from "../components/ui";
import { formatAge, formatClock } from "../lib/format";

function ServiceRow({ icon: Icon, name, detail, status }) {
  const tone = status === "online" ? "success" : status === "degraded" ? "amber" : "danger";
  return (
    <div className="event" style={{ gridTemplateColumns: "18px minmax(0,1fr) auto" }}>
      <Icon size={16} className="dim" />
      <div>
        <div className="message" style={{ fontWeight: 600 }}>{name}</div>
        <div className="meta">{detail}</div>
      </div>
      <Badge tone={tone}>{status}</Badge>
    </div>
  );
}

export default function SystemView({ data, onSeed, seeding }) {
  const { firebase, mqtt, demo, ambulances, junctions, hospitals, events, now } = data;

  const reporting = ambulances.filter((a) => a.ageSeconds != null && a.ageSeconds < 120).length;
  const gpsLocked = ambulances.filter((a) => a.telemetry?.gpsFix).length;
  const rfidCount = ambulances.filter((a) => a.rfidTagId).length;
  const dataAge = data.lastDataAt != null ? Math.max(0, (now - data.lastDataAt) / 1000) : null;
  const mqttOnline = mqtt.status === "live";
  const mqttLast = mqtt.lastMessageAt ? formatAge((now - mqtt.lastMessageAt) / 1000) : "no messages yet";

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>System status</h2>
          <p>Connectivity, telemetry freshness and demo controls.</p>
        </div>
        <Badge tone={demo.mode ? "amber" : firebase.online ? "success" : "danger"}>
          {demo.mode ? "Demo scenario" : firebase.online ? "Live data" : "Offline"}
        </Badge>
      </div>

      <div className="grid-stats">
        <div className="stat tone-green">
          <div className="icon"><Database size={20} /></div>
          <div>
            <div className="value">{firebase.online ? "Live" : "Offline"}</div>
            <div className="label">Firebase RTDB</div>
            <div className="detail">{dataAge != null ? `updated ${formatAge(dataAge)}` : "no data yet"}</div>
          </div>
        </div>
        <div className="stat tone-blue">
          <div className="icon"><Wifi size={20} /></div>
          <div>
            <div className="value">{mqttOnline ? "Live" : mqtt.status}</div>
            <div className="label">MQTT broker</div>
            <div className="detail">{mqttLast}</div>
          </div>
        </div>
        <div className="stat tone-amber">
          <div className="icon"><Satellite size={20} /></div>
          <div>
            <div className="value">{gpsLocked}</div>
            <div className="label">GPS-locked units</div>
            <div className="detail">{reporting} reporting</div>
          </div>
        </div>
        <div className="stat tone-red">
          <div className="icon"><Activity size={20} /></div>
          <div>
            <div className="value">{events.length}</div>
            <div className="label">Events received</div>
            <div className="detail">{junctions.length} junctions · {hospitals.length} hospitals</div>
          </div>
        </div>
      </div>

      <div className="split">
        <Panel title="Service health" icon={ServerCog}>
          <div className="event-feed" style={{ maxHeight: "none" }}>
            <ServiceRow
              icon={Database}
              name="Firebase Realtime Database"
              detail={firebase.error ? firebase.error.message : "smart-ambulance-36f9d"}
              status={firebase.online ? "online" : "offline"}
            />
            <ServiceRow
              icon={Wifi}
              name="MQTT broker (HiveMQ)"
              detail={mqtt.error || "wss://broker.hivemq.com:8884/mqtt"}
              status={mqttOnline ? "online" : mqtt.status === "connecting" ? "degraded" : "offline"}
            />
            <ServiceRow
              icon={Radio}
              name="LoRa junction links"
              detail={`${junctions.filter((j) => j.online).length}/${junctions.length} junctions with recent heartbeat`}
              status={junctions.some((j) => j.online) ? "online" : "degraded"}
            />
            <ServiceRow
              icon={Tag}
              name="RFID stop-line readers"
              detail={`${rfidCount} ${rfidCount === 1 ? "vehicle" : "vehicles"} with authorised tags`}
              status={rfidCount ? "online" : "degraded"}
            />
            <ServiceRow
              icon={Satellite}
              name="GPS telemetry"
              detail={`${gpsLocked} of ${ambulances.length} ambulances with GPS fix`}
              status={gpsLocked ? "online" : "degraded"}
            />
          </div>
        </Panel>

        <div className="stack">
          <Panel title="Demo & seeding" icon={FlaskConical}>
            <div style={{ display: "grid", gap: 14 }}>
              <Toggle on={demo.mode} onChange={demo.setMode} label="Run simulated emergency corridor" />
              <p className="dim" style={{ fontSize: "0.82rem" }}>
                The demo scenario drives a simulated ambulance along the route so the console can be shown
                without ESP32 hardware. Turn it off to return to live Firebase/MQTT data.
              </p>
              <div className="btn-row">
                <button className="btn btn-ghost btn-sm" disabled={seeding} onClick={() => onSeed("hospitals")}>
                  {seeding ? "Seeding..." : "Seed demo hospitals"}
                </button>
                <button className="btn btn-ghost btn-sm" disabled={seeding} onClick={() => onSeed("demo")}>
                  Seed demo users & vehicles
                </button>
              </div>
            </div>
          </Panel>

          <Panel title="Connection" icon={ShieldCheck}>
            <KeyValue
              items={[
                { k: "Project", v: "smart-ambulance-36f9d" },
                { k: "Database URL", v: <span className="mono" style={{ fontSize: "0.74rem" }}>smart-ambulance-36f9d-default-rtdb</span> },
                { k: "MQTT status", v: mqtt.status },
                { k: "Data source", v: demo.mode ? "Demo scenario" : "Firebase + MQTT" },
                { k: "Last MQTT message", v: mqttLast },
                { k: "Local time", v: formatClock(now) },
              ]}
            />
          </Panel>

        </div>
      </div>
    </div>
  );
}
