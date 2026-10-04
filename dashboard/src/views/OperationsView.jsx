import React, { Suspense, lazy } from "react";
import {
  Activity,
  Ambulance,
  Clock,
  Crosshair,
  Gauge,
  Hospital as HospitalIcon,
  MapPin,
  Navigation,
  Radio,
  Siren,
  TrafficCone,
} from "lucide-react";
import EventFeed from "../components/EventFeed";
import { Badge, Dot, Empty, Panel, SignalDots, Stat, Toggle, preemptionLabel, signalLabel } from "../components/ui";
import { formatDistance, formatEta } from "../lib/format";
import { compassLabel } from "../lib/geo";
import { severityLabel, severityTone } from "../lib/model";

// Leaflet and its CSS are the heaviest dependency in the console and are only
// needed once the map scrolls into view, so the map lives in its own chunk.
const MapPanel = lazy(() => import("../components/MapPanel"));

function MapFallback() {
  return (
    <div className="empty" style={{ minHeight: 320 }}>
      <strong>Loading map…</strong>
    </div>
  );
}

function CorridorPanel({ corridor, onRelease, onFocus }) {
  const { ambulance, destination, nearestJunction, position, speed, headingDeg } = corridor;
  const hospitalMeters = corridor.hospitalMeters;

  return (
    <Panel
      title="Active response"
      icon={Siren}
      hint={ambulance.ambulanceId}
      className="corridor-card"
      actions={
        <Badge tone={severityTone(ambulance.severity)}>{severityLabel(ambulance.severity)}</Badge>
      }
    >
      <div className="row-top" style={{ marginBottom: 14 }}>
        <div className="row-title">
          <Dot live />
          <span>Ambulance {ambulance.ambulanceId}</span>
        </div>
        <button className="btn btn-danger-ghost btn-sm" onClick={() => onRelease?.(ambulance)}>
          Release corridor
        </button>
      </div>

      <div className="corridor-route">
        <div className="corridor-step">
          <div className="step-icon" style={{ color: "var(--red)" }}>
            <Ambulance size={16} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700 }}>Ambulance position</div>
            <div className="dim mono" style={{ fontSize: "0.78rem" }}>
              {position ? `${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}` : "Waiting for GPS"}
              {headingDeg != null ? ` · ${compassLabel(headingDeg) || `${Math.round(headingDeg)}°`}` : ""}
            </div>
          </div>
        </div>
        <div className="corridor-line" />
        <div className="corridor-step">
          <div className="step-icon" style={{ color: nearestJunction?.signalState === "priority_active" ? "var(--green)" : "var(--blue)" }}>
            <TrafficCone size={16} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700 }}>{nearestJunction?.name || "Next junction"}</div>
            <div className="dim" style={{ fontSize: "0.78rem" }}>
              {nearestJunction
                ? `${formatDistance(corridor.nearestJunctionMeters)} away · ${signalLabel(nearestJunction.signalState)}`
                : "No junction in range"}
            </div>
          </div>
          {nearestJunction ? (
            <Badge tone={nearestJunction.signalState === "priority_active" ? "success" : "neutral"}>
              {preemptionLabel(nearestJunction.preemptionMode)}
            </Badge>
          ) : null}
        </div>
        <div className="corridor-line" />
        <div className="corridor-step">
          <div className="step-icon" style={{ color: "var(--green)" }}>
            <HospitalIcon size={16} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700 }}>{destination?.name || "Destination hospital"}</div>
            <div className="dim" style={{ fontSize: "0.78rem" }}>
              {destination
                ? `${formatDistance(hospitalMeters)} · ETA ${formatEta(corridor.hospitalEta)} · ${destination.bedsAvailable ?? "--"} beds`
                : "No destination selected"}
            </div>
          </div>
        </div>
      </div>

      <div className="grid-stats" style={{ marginTop: 16, gridTemplateColumns: "1fr 1fr" }}>
        <Stat icon={Clock} tone="amber" label="Hospital ETA" value={formatEta(corridor.hospitalEta)} detail={formatDistance(hospitalMeters)} />
        <Stat icon={Gauge} tone="green" label="Speed" value={speed != null ? `${Math.round(speed)}` : "--"} detail="km/h" />
      </div>

      <div className="btn-row" style={{ marginTop: 14 }}>
        <button className="btn btn-ghost btn-sm" onClick={() => onFocus?.(position)}>
          <Crosshair size={15} /> Centre map
        </button>
        <button className="btn btn-ghost btn-sm" onClick={() => onFocus?.(destination?.location)}>
          <MapPin size={15} /> Hospital
        </button>
      </div>
    </Panel>
  );
}

export default function OperationsView({ data, onRelease, follow, setFollow, onFocus }) {
  const { corridors, junctions, hospitals, ambulances, events, activeTrips } = data;
  const priorityJunctions = junctions.filter((j) => j.signalState === "priority_active").length;
  const onlineJunctions = junctions.filter((j) => j.online).length;
  const inboundTotal = hospitals.reduce((sum, h) => sum + (h.bedsAvailable || 0), 0);

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>Live operations</h2>
          <p>Real-time ambulance corridor, junction preemption and hospital routing.</p>
        </div>
        <Toggle on={follow} onChange={setFollow} label="Follow ambulance" />
      </div>

      <div className="grid-stats">
        <Stat
          icon={Ambulance}
          tone="red"
          label="Active emergencies"
          value={corridors.length}
          detail={`${ambulances.length} ${ambulances.length === 1 ? "ambulance" : "ambulances"} registered`}
        />
        <Stat
          icon={TrafficCone}
          tone="green"
          label="Priority signals"
          value={priorityJunctions}
          detail={`${onlineJunctions}/${junctions.length} junctions online`}
        />
        <Stat
          icon={HospitalIcon}
          tone="blue"
          label="Beds available"
          value={inboundTotal}
          detail={`${hospitals.length} hospitals linked`}
        />
        <Stat
          icon={Activity}
          tone="amber"
          label="Active trips"
          value={activeTrips.length}
          detail={`${events.length} events logged`}
        />
      </div>

      <div className="ops-grid">
        <Panel className="map-card" title={null}>
          <Suspense fallback={<MapFallback />}>
            <MapPanel corridors={corridors} junctions={junctions} hospitals={hospitals} follow={follow} />
          </Suspense>
        </Panel>

        {corridors.length ? (
          <CorridorPanel corridor={corridors[0]} onRelease={onRelease} onFocus={onFocus} />
        ) : (
          <Panel title="Active response" icon={Siren}>
            <Empty
              icon={Ambulance}
              title="No active emergency"
              detail="A driver starts a trip from the mobile app and the corridor will appear here automatically."
            />
            <div style={{ marginTop: 14 }}>
              <div className="eyebrow" style={{ marginBottom: 8 }}>System ready</div>
              <div className="kv">
                <div><div className="k">Junctions online</div><div className="v">{onlineJunctions}/{junctions.length}</div></div>
                <div><div className="k">Ambulances</div><div className="v">{ambulances.length}</div></div>
                <div><div className="k">Hospitals</div><div className="v">{hospitals.length}</div></div>
              </div>
            </div>
          </Panel>
        )}
      </div>

      <div className="split">
        <Panel title="Junction status" icon={TrafficCone} hint="live">
          {junctions.length ? (
            <div className="list">
              {junctions.map((junction) => (
                <div className="row-card" key={junction.junctionId}>
                  <div className="row-top">
                    <div className="row-title">
                      <SignalDots state={junction.signalState} offline={!junction.online} />
                      <span>{junction.name}</span>
                    </div>
                    <Badge tone={!junction.online ? "neutral" : junction.signalState === "priority_active" ? "success" : "neutral"}>
                      {junction.online ? signalLabel(junction.signalState) : "Offline"}
                    </Badge>
                  </div>
                  <div className="dim" style={{ fontSize: "0.8rem" }}>
                    {junction.junctionId} · {junction.lane || "lane --"}
                    {junction.distanceMeters != null ? ` · ambulance ${formatDistance(junction.distanceMeters)}` : ""}
                    {junction.rssi != null ? ` · ${junction.rssi} dBm` : ""}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <Empty icon={TrafficCone} title="No junctions configured" detail="Seed junctions from the System tab or register them in the admin app." />
          )}
        </Panel>

        <Panel title="Live event log" icon={Radio} hint={`${events.length}`} actions={<Badge tone="info"><Navigation size={11} /> realtime</Badge>}>
          <EventFeed events={events} limit={12} />
        </Panel>
      </div>
    </div>
  );
}
