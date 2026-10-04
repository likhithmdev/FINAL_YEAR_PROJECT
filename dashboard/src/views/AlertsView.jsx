import React, { useState } from "react";
import { AlertTriangle, Bell, ShieldAlert, Siren } from "lucide-react";
import { Badge, Dot, Empty, Panel } from "../components/ui";
import { formatAge, formatDateTime, formatDistance } from "../lib/format";
import { severityLabel, severityTone } from "../lib/model";

const FILTERS = [
  { key: "all", label: "All" },
  { key: "police", label: "Junction" },
  { key: "hospital", label: "Hospital" },
];

function AlertRow({ alert }) {
  const isPolice = alert.kind === "police";
  return (
    <div className="row-card">
      <div className="row-top">
        <div className="row-title">
          <Dot tone={alert.tone === "danger" ? "red" : alert.tone === "amber" ? "amber" : alert.tone === "success" ? "green" : "blue"} live={alert.status === "incoming" || alert.status === "preemption_active"} />
          <span className="mono">{alert.ambulanceId || "AMB"}</span>
          <Badge tone={severityTone(alert.severity)}>{severityLabel(alert.severity)}</Badge>
          <Badge tone={isPolice ? "info" : "success"}>{isPolice ? "Junction" : "Hospital"}</Badge>
        </div>
        <Badge tone={alert.tone}>{alert.label}</Badge>
      </div>

      <div className="kv" style={{ marginTop: 4 }}>
        <div>
          <div className="k">{isPolice ? "Junction" : "Hospital"}</div>
          <div className="v">{alert.groupId}</div>
        </div>
        <div>
          <div className="k">Trip</div>
          <div className="v mono">{alert.tripId || "—"}</div>
        </div>
        <div>
          <div className="k">Preemption</div>
          <div className="v">{alert.preemptionMode || "—"}</div>
        </div>
        <div>
          <div className="k">Distance</div>
          <div className="v">{alert.distanceMeters != null ? formatDistance(alert.distanceMeters) : "—"}</div>
        </div>
        <div>
          <div className="k">ETA</div>
          <div className="v">{alert.eta || "—"}</div>
        </div>
        <div>
          <div className="k">Updated</div>
          <div className="v">{formatAge(alert.ageSeconds)}</div>
        </div>
      </div>

      {alert.message ? <div className="dim" style={{ fontSize: "0.8rem" }}>{alert.message}</div> : null}
    </div>
  );
}

export default function AlertsView({ data }) {
  const [filter, setFilter] = useState("all");
  const all = [...data.policeAlerts, ...data.hospitalAlerts].sort(
    (a, b) => (b.updatedAt || 0) - (a.updatedAt || 0),
  );
  const alerts = filter === "all" ? all : all.filter((alert) => alert.kind === filter);
  const active = all.filter((alert) => alert.status !== "completed").length;

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>Alerts</h2>
          <p>Unified junction and hospital alert stream with response status.</p>
        </div>
        <div className="btn-row">
          {FILTERS.map((option) => (
            <button
              key={option.key}
              className={`btn btn-sm ${filter === option.key ? "btn-ghost" : "btn-ghost"}`}
              style={filter === option.key ? { borderColor: "var(--amber)", color: "var(--amber)" } : undefined}
              onClick={() => setFilter(option.key)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid-stats">
        <div className="stat tone-red">
          <div className="icon"><Bell size={20} /></div>
          <div><div className="value">{active}</div><div className="label">Active alerts</div></div>
        </div>
        <div className="stat tone-blue">
          <div className="icon"><ShieldAlert size={20} /></div>
          <div><div className="value">{data.policeAlerts.length}</div><div className="label">Junction alerts</div></div>
        </div>
        <div className="stat tone-green">
          <div className="icon"><Siren size={20} /></div>
          <div><div className="value">{data.hospitalAlerts.length}</div><div className="label">Hospital alerts</div></div>
        </div>
      </div>

      <Panel title="Alert stream" icon={AlertTriangle} hint={`${alerts.length} shown`}>
        {alerts.length ? (
          <div className="list">
            {alerts.map((alert) => (
              <AlertRow key={alert.id} alert={alert} />
            ))}
          </div>
        ) : (
          <Empty icon={AlertTriangle} title="No alerts" detail="Alerts appear when an emergency trip starts and cross-role notifications are written." />
        )}
      </Panel>
    </div>
  );
}
