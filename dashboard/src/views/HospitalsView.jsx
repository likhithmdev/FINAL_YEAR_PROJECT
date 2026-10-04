import React from "react";
import { BedDouble, Hospital as HospitalIcon, Phone, Siren, Stethoscope, UserCheck, Users } from "lucide-react";
import { Badge, Dot, Empty, KeyValue, Panel } from "../components/ui";
import { formatAge, formatDistance, formatEta } from "../lib/format";
import { haversineMeters, etaSeconds } from "../lib/geo";
import { deriveCorridor, severityLabel, severityTone } from "../lib/model";

const BAY_ITEMS = [
  { key: "responseTeam", label: "Response team", icon: Users },
  { key: "bedBay", label: "Bed / bay", icon: BedDouble },
  { key: "doctorOnDuty", label: "Doctor on duty", icon: Stethoscope },
  { key: "patientReceived", label: "Patient received", icon: UserCheck },
];

function BayReadiness({ readiness }) {
  const level = BAY_ITEMS.filter((item) => readiness?.[item.key]).length;
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span className="eyebrow">Bay readiness</span>
        <span className="mono" style={{ fontSize: "0.78rem" }}>{level} / 4</span>
      </div>
      <div className="progress" style={{ marginBottom: 10 }}>
        <span style={{ width: `${(level / 4) * 100}%` }} />
      </div>
      <div className="btn-row">
        {BAY_ITEMS.map((item) => {
          const on = Boolean(readiness?.[item.key]);
          const Icon = item.icon;
          return (
            <Badge key={item.key} tone={on ? "success" : "neutral"}>
              <Icon size={11} /> {item.label} {on ? "ready" : "pending"}
            </Badge>
          );
        })}
      </div>
    </div>
  );
}

export default function HospitalsView({ data }) {
  const { hospitals, hospitalAlerts, ambulances, junctions } = data;
  const alertByHospital = new Map();
  hospitalAlerts
    .filter((alert) => alert.status !== "completed")
    .forEach((alert) => {
      if (!alertByHospital.has(alert.groupId)) alertByHospital.set(alert.groupId, alert);
    });

  const emergencyAmbulance = ambulances.find((a) => a.emergencyActive) || null;
  const corridor = emergencyAmbulance ? deriveCorridor({ ambulance: emergencyAmbulance, junctions, hospitals }) : null;

  function distanceFor(hospital) {
    if (corridor?.position && hospital.location) {
      const meters = haversineMeters(corridor.position, hospital.location);
      return { meters, eta: etaSeconds(meters, corridor.speed) };
    }
    return { meters: null, eta: null };
  }

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h2>Hospital coordination</h2>
          <p>Incoming alerts, bed availability and trauma-bay readiness.</p>
        </div>
        <Badge tone={hospitalAlerts.length ? "danger" : "success"}>
          {hospitalAlerts.length
            ? `${hospitalAlerts.length} active ${hospitalAlerts.length === 1 ? "alert" : "alerts"}`
            : "No active alerts"}
        </Badge>
      </div>

      {hospitals.length ? (
        <div className="split">
          {hospitals.map((hospital) => {
            const alert = alertByHospital.get(hospital.hospitalId);
            const { meters, eta } = distanceFor(hospital);
            const isDestination = emergencyAmbulance?.destinationHospitalId === hospital.hospitalId;
            return (
              <Panel
                key={hospital.hospitalId}
                title={hospital.name}
                icon={HospitalIcon}
                actions={
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    {isDestination ? <Dot live /> : null}
                    <Badge tone={hospital.emergencyAvailable ? "success" : "danger"}>
                      {hospital.emergencyAvailable ? "Emergency open" : "Emergency full"}
                    </Badge>
                  </div>
                }
              >
                <KeyValue
                  items={[
                    { k: "Hospital ID", v: <span className="mono">{hospital.hospitalId}</span> },
                    { k: "Beds available", v: hospital.bedsAvailable ?? "—" },
                    { k: "Distance (active unit)", v: meters != null ? formatDistance(meters) : hospital.staticDistance || "—" },
                    { k: "ETA (active unit)", v: eta != null ? formatEta(eta) : hospital.staticEta || "—" },
                  ]}
                />

                {hospital.phone ? (
                  <div className="dim" style={{ fontSize: "0.78rem", marginTop: 10, display: "inline-flex", gap: 6, alignItems: "center" }}>
                    <Phone size={12} /> {hospital.phone}
                  </div>
                ) : null}

                {alert ? (
                  <div className="row-card" style={{ marginTop: 14, borderColor: "color-mix(in srgb, var(--red) 40%, transparent)" }}>
                    <div className="row-top">
                      <div className="row-title">
                        <Siren size={16} style={{ color: "var(--red)" }} />
                        <span className="mono">{alert.ambulanceId}</span>
                      </div>
                      <Badge tone={severityTone(alert.severity)}>{severityLabel(alert.severity)}</Badge>
                    </div>
                    <div className="dim" style={{ fontSize: "0.8rem" }}>
                      {alert.message || "Ambulance incoming"} · ETA {alert.eta || formatEta(eta) || "—"}
                      {alert.ageSeconds != null ? ` · updated ${formatAge(alert.ageSeconds)}` : ""}
                    </div>
                    <BayReadiness readiness={alert.bayReadiness} />
                  </div>
                ) : (
                  <div className="dim" style={{ fontSize: "0.8rem", marginTop: 12 }}>No inbound ambulance for this facility.</div>
                )}
              </Panel>
            );
          })}
        </div>
      ) : (
        <Empty icon={HospitalIcon} title="No hospitals configured" detail="Seed the demo hospital set from the System tab, or add hospitals in the admin app." />
      )}
    </div>
  );
}
