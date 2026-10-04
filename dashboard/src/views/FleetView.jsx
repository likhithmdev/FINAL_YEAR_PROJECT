import React from "react";
import { Ambulance, Gauge, MapPin, Radio, Satellite, Tag } from "lucide-react";
import { Badge, Dot, Empty, KeyValue, Panel } from "../components/ui";
import { formatAge, formatDistance, formatEta } from "../lib/format";
import { deriveCorridor, severityLabel, severityTone } from "../lib/model";

export default function FleetView({ data, onFocus }) {
  const { ambulances, junctions, hospitals } = data;

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>Ambulance fleet</h2>
          <p>Vehicle status, LoRa telemetry health and live position for every unit.</p>
        </div>
        <Badge tone="info">{ambulances.length} {ambulances.length === 1 ? "unit" : "units"}</Badge>
      </div>

      {ambulances.length ? (
        <div className="split">
          {ambulances.map((ambulance) => {
            const corridor = deriveCorridor({ ambulance, junctions, hospitals });
            const telemetry = ambulance.telemetry || {};
            const position = corridor.position;
            return (
              <Panel
                key={ambulance.ambulanceId}
                title={<span className="mono">{ambulance.ambulanceId}</span>}
                icon={Ambulance}
                actions={
                  ambulance.emergencyActive ? (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <Dot live />
                      <Badge tone={severityTone(ambulance.severity)}>{severityLabel(ambulance.severity)}</Badge>
                    </div>
                  ) : (
                    <Badge tone="success">Available</Badge>
                  )
                }
              >
                <KeyValue
                  items={[
                    { k: "Driver", v: ambulance.driverId || "—" },
                    { k: "RFID tag", v: ambulance.rfidTagId || "—" },
                    { k: "LoRa node", v: ambulance.loraNodeId || "—" },
                    { k: "Destination", v: corridor.destination?.name || (ambulance.destinationHospitalId || "—") },
                  ]}
                />

                <div style={{ marginTop: 14 }}>
                  <div className="eyebrow" style={{ marginBottom: 8 }}>LoRa telemetry</div>
                  <div className="kv">
                    <div>
                      <div className="k">Distance to junction</div>
                      <div className="v">{telemetry.distanceMeters != null ? formatDistance(telemetry.distanceMeters) : "—"}</div>
                    </div>
                    <div>
                      <div className="k">RSSI</div>
                      <div className="v">{telemetry.rssi != null ? `${telemetry.rssi} dBm` : "—"}</div>
                    </div>
                    <div>
                      <div className="k">GPS fix</div>
                      <div className="v">{telemetry.gpsFix == null ? "—" : telemetry.gpsFix ? "Locked" : "No fix"}</div>
                    </div>
                    <div>
                      <div className="k">Speed</div>
                      <div className="v">{telemetry.speedKmph != null ? `${Math.round(telemetry.speedKmph)} km/h` : "—"}</div>
                    </div>
                    <div>
                      <div className="k">Approaching</div>
                      <div className="v">{telemetry.approaching == null ? "—" : telemetry.approaching ? "Yes" : "No"}</div>
                    </div>
                    <div>
                      <div className="k">Preempt eligible</div>
                      <div className="v">{telemetry.preemptionEligible == null ? "—" : telemetry.preemptionEligible ? "Yes" : "No"}</div>
                    </div>
                  </div>
                </div>

                <div className="btn-row" style={{ marginTop: 14 }}>
                  <span className="hint" style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                    <Satellite size={13} /> {position ? `${position.lat.toFixed(4)}, ${position.lng.toFixed(4)}` : "No position"}
                  </span>
                  <span className="hint" style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                    <Radio size={13} /> {ambulance.ageSeconds != null ? formatAge(ambulance.ageSeconds) : "no heartbeat"}
                  </span>
                  {corridor.hospitalEta != null ? (
                    <span className="hint" style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                      <Gauge size={13} /> ETA {formatEta(corridor.hospitalEta)}
                    </span>
                  ) : null}
                  {position ? (
                    <button className="btn btn-ghost btn-sm" onClick={() => onFocus?.(position)}>
                      <MapPin size={14} /> Locate
                    </button>
                  ) : null}
                </div>

                <div className="dim" style={{ fontSize: "0.74rem", marginTop: 10, display: "inline-flex", gap: 6, alignItems: "center" }}>
                  <Tag size={12} /> Source: {telemetry.source || ambulance.lastLocation?.source || "unknown"}
                </div>
              </Panel>
            );
          })}
        </div>
      ) : (
        <Empty icon={Ambulance} title="No ambulances registered" detail="Seed demo data from the System tab or register a vehicle in the admin app." />
      )}
    </div>
  );
}
