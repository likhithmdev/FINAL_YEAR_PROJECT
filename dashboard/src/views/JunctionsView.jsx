import React, { useState } from "react";
import {
  CheckCircle2,
  Clock,
  FlaskConical,
  RadioTower,
  RefreshCcw,
  Siren,
  TrafficCone,
  Wifi,
  WifiOff,
} from "lucide-react";
import { Badge, Empty, KeyValue, Panel, SignalDots, signalLabel, preemptionLabel } from "../components/ui";
import { formatAge, formatDistance } from "../lib/format";

function JunctionCard({ junction, onAction, busy }) {
  const [showConfig, setShowConfig] = useState(false);
  return (
    <Panel
      title={
        <span>
          {junction.name} <span className="mono dim-2" style={{ fontSize: "0.78rem" }}>{junction.junctionId}</span>
        </span>
      }
      hint={junction.lane || undefined}
      actions={
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <SignalDots state={junction.signalState} offline={!junction.online} />
          {junction.online ? <Wifi size={16} className="dim" /> : <WifiOff size={16} className="dim-2" />}
        </div>
      }
    >
      <div style={{ marginBottom: 12 }}>
        <Badge tone={!junction.online ? "neutral" : junction.signalState === "priority_active" ? "success" : junction.signalState === "timeout_restore" ? "amber" : "info"}>
          {junction.online ? signalLabel(junction.signalState) : "No heartbeat"}
        </Badge>{" "}
        {junction.preemptionMode && junction.preemptionMode !== "none" ? (
          <Badge tone="danger">{preemptionLabel(junction.preemptionMode)}</Badge>
        ) : null}
      </div>

      <KeyValue
        items={[
          { k: "Active ambulance", v: junction.activeAmbulanceId || "—" },
          { k: "Approach distance", v: junction.distanceMeters != null ? formatDistance(junction.distanceMeters) : "—" },
          { k: "LoRa RSSI", v: junction.rssi != null ? `${junction.rssi} dBm` : "—" },
          { k: "Last clearance", v: junction.lastDwellTime || "—" },
          { k: "Heartbeat", v: junction.ageSeconds != null ? formatAge(junction.ageSeconds) : "no data" },
        ]}
      />

      <div className="btn-row" style={{ marginTop: 14 }}>
        <button
          className="btn btn-ghost btn-sm"
          disabled={busy || !junction.activeAmbulanceId}
          onClick={() => onAction("manual_priority", junction)}
        >
          <Siren size={14} /> Hold priority
        </button>
        <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("reset", junction)}>
          <CheckCircle2 size={14} /> Restore normal
        </button>
        <button className="btn btn-ghost btn-sm" onClick={() => setShowConfig((value) => !value)}>
          <TrafficCone size={14} /> {showConfig ? "Hide" : "Thresholds"}
        </button>
      </div>

      {showConfig ? (
        <div style={{ marginTop: 14 }}>
          <KeyValue
            items={[
              { k: "Approach threshold", v: `${junction.approachThresholdMeters} m` },
              { k: "Bearing tolerance", v: `${junction.bearingToleranceDeg}°` },
              { k: "RSSI fallback", v: `${junction.rssiFallbackThresholdDbm} dBm` },
              { k: "GPS timeout", v: `${Math.round(junction.gpsPacketTimeoutMs / 1000)} s` },
              { k: "Clearance timeout", v: `${Math.round(junction.clearanceTimeoutMs / 1000)} s` },
            ]}
          />
        </div>
      ) : null}
    </Panel>
  );
}

export default function JunctionsView({ data, onAction, busy }) {
  const { junctions } = data;
  const priority = junctions.filter((j) => j.signalState === "priority_active").length;
  const timeout = junctions.filter((j) => j.signalState === "timeout_restore").length;
  const normal = junctions.length - priority - timeout;

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>Junction control</h2>
          <p>LoRa approach tracking, RFID clearance and local signal preemption.</p>
        </div>
        <div className="btn-row">
          <Badge tone="success">{priority} priority</Badge>
          <Badge tone="amber">{timeout} timeout</Badge>
          <Badge tone="neutral">{normal} normal</Badge>
        </div>
      </div>

      {junctions.length ? (
        <div className="split">
          {junctions.map((junction) => (
            <JunctionCard key={junction.junctionId} junction={junction} onAction={onAction} busy={busy} />
          ))}
        </div>
      ) : (
        <Empty icon={TrafficCone} title="No junctions" detail="Register junctions in the admin app or seed them from the System tab." />
      )}

      <Panel
        title="Tabletop demo injection"
        icon={FlaskConical}
        hint="writes to Firebase junction events"
        actions={<span className="hint">Use when no ESP32 hardware is present</span>}
      >
        <div className="list">
          {junctions.map((junction) => (
            <div className="row-card" key={junction.junctionId}>
              <div className="row-top">
                <div className="row-title">
                  <RadioTower size={16} className="dim" />
                  <span>{junction.name}</span>
                  <span className="mono dim-2" style={{ fontSize: "0.78rem" }}>{junction.junctionId}</span>
                </div>
                <span className="hint dim-2">simulate hardware</span>
              </div>
              <div className="btn-row">
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("sim_gps", junction)}>
                  <Siren size={14} /> GPS preempt
                </button>
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("sim_rssi", junction)}>
                  <RadioTower size={14} /> RSSI fallback
                </button>
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("sim_rfid", junction)}>
                  <CheckCircle2 size={14} /> RFID clearance
                </button>
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("sim_timeout", junction)}>
                  <Clock size={14} /> Safety timeout
                </button>
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onAction("reset", junction)}>
                  <RefreshCcw size={14} /> Reset
                </button>
              </div>
            </div>
          ))}
        </div>
      </Panel>
    </div>
  );
}
