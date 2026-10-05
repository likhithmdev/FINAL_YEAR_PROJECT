import React, { Suspense, lazy, useCallback, useState } from "react";
import {
  Activity,
  Ambulance,
  Bell,
  Hospital as HospitalIcon,
  Radio,
  ServerCog,
  Siren,
  TrafficCone,
} from "lucide-react";

import {
  writeAmbulanceStatus,
  writeJunction,
  writeJunctionEvent,
  writeLoRaTelemetry,
  writeTrip,
} from "./integrations/firebaseClient";
import { useOperationsData } from "./hooks/useOperationsData";
import { useAuth } from "./hooks/useAuth";
import { Badge, Dot } from "./components/ui";
import LoginGate from "./components/LoginGate";

// Each console view is its own chunk, so the initial load only pays for the
// shell plus the live-operations screen the operator actually lands on.
const OperationsView = lazy(() => import("./views/OperationsView"));
const JunctionsView = lazy(() => import("./views/JunctionsView"));
const FleetView = lazy(() => import("./views/FleetView"));
const HospitalsView = lazy(() => import("./views/HospitalsView"));
const AlertsView = lazy(() => import("./views/AlertsView"));
const SystemView = lazy(() => import("./views/SystemView"));
import { seedDemoHospitals, seedDemoInfrastructure } from "./lib/seed";
import { ageSeconds } from "./lib/format";

const AMBULANCE_ID = "AMB001";
const TRIP_ID = "TRIP001";
const RFID_TAG = "RFID_TAG_001";

const NAV = [
  { key: "operations", label: "Live ops", icon: Activity },
  { key: "junctions", label: "Junctions", icon: TrafficCone },
  { key: "fleet", label: "Ambulances", icon: Ambulance },
  { key: "hospitals", label: "Hospitals", icon: HospitalIcon },
  { key: "alerts", label: "Alerts", icon: Bell },
  { key: "system", label: "System", icon: ServerCog },
];

const randomBetween = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

// Shown while a lazily-imported view chunk is in flight. Reuses the existing
// card/empty styling so it never flashes a different-looking screen.
function ViewFallback() {
  return (
    <div className="card" style={{ display: "grid", placeItems: "center", minHeight: 240 }}>
      <div className="empty">
        <Activity size={26} />
        <strong>Loading view…</strong>
      </div>
    </div>
  );
}

// The authenticated console. Mounted only once an operator is signed in, so
// the data subscriptions below never run against rules that would deny them.
function Console({ operator, onSignOut }) {
  const data = useOperationsData();
  const [view, setView] = useState("operations");
  const [follow, setFollow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [focus, setFocus] = useState(null);
  const [toasts, setToasts] = useState([]);

  const toast = useCallback((message, kind = "info") => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((current) => [...current, { id, message, kind }]);
    setTimeout(() => setToasts((current) => current.filter((item) => item.id !== id)), 4200);
  }, []);

  const focusOn = useCallback((point) => {
    if (point && typeof point.lat === "number") {
      setFocus({ lat: point.lat, lng: point.lng, nonce: Date.now() });
    }
  }, []);

  const activeAlerts = data.policeAlerts.filter((a) => a.status !== "completed").length +
    data.hospitalAlerts.filter((a) => a.status !== "completed").length;
  const priorityJunctions = data.junctions.filter((j) => j.signalState === "priority_active").length;
  const emergencyCount = data.corridors.length;

  const badgeFor = (key) => {
    if (key === "junctions" && priorityJunctions) return priorityJunctions;
    if (key === "fleet" && emergencyCount) return emergencyCount;
    if (key === "alerts" && activeAlerts) return activeAlerts;
    return null;
  };

  // ------------------------------------------------------------ actions --
  async function handleJunctionAction(type, junction) {
    if (data.demo.mode) {
      toast("Demo scenario is active — junction actions are disabled.", "info");
      return;
    }
    setBusy(true);
    const now = Date.now();
    try {
      if (type === "sim_gps" || type === "sim_rssi") {
        const isGps = type === "sim_gps";
        const distanceMeters = isGps ? randomBetween(320, 500) : null;
        const rssi = isGps ? -randomBetween(68, 80) : -randomBetween(58, 66);
        await writeJunction(junction.junctionId, {
          activeAmbulanceId: AMBULANCE_ID,
          activeLane: junction.lane || null,
          preemptionMode: isGps ? "gps_lora" : "rssi_fallback",
          distanceMeters,
          rssi,
          signalState: "priority_active",
          updatedAt: now,
        });
        await writeLoRaTelemetry(junction.junctionId, AMBULANCE_ID, {
          ambulanceId: AMBULANCE_ID,
          tripId: TRIP_ID,
          gpsFix: isGps,
          rssi,
          distanceMeters,
          approaching: true,
          preemptionEligible: true,
          source: isGps ? "gps_lora" : "rssi_fallback",
          updatedAt: now,
        });
        await writeJunctionEvent({
          ambulanceId: AMBULANCE_ID,
          eventType: isGps ? "gps_preempt_started" : "rssi_preempt_started",
          junctionId: junction.junctionId,
          junctionName: junction.name,
          lane: junction.lane || null,
          preemptionMode: isGps ? "gps_lora" : "rssi_fallback",
          distanceMeters,
          rssi,
          rfidTagId: RFID_TAG,
          timestamp: now,
        });
        toast(`${isGps ? "GPS + LoRa" : "RSSI fallback"} preemption issued at ${junction.name}`, "success");
      } else if (type === "sim_rfid") {
        const dwellTime = `${randomBetween(6, 14)}s`;
        await writeJunction(junction.junctionId, {
          activeAmbulanceId: null,
          activeLane: null,
          preemptionMode: "none",
          lastDwellTime: dwellTime,
          signalState: "normal",
          updatedAt: now,
        });
        await writeJunctionEvent({
          ambulanceId: AMBULANCE_ID,
          eventType: "rfid_clearance",
          junctionId: junction.junctionId,
          junctionName: junction.name,
          rfidTagId: RFID_TAG,
          preemptionMode: "rfid_clearance",
          dwellTime,
          timestamp: now,
        });
        toast(`RFID clearance at ${junction.name}`, "success");
      } else if (type === "sim_timeout") {
        await writeJunction(junction.junctionId, {
          activeAmbulanceId: null,
          activeLane: null,
          preemptionMode: "none",
          signalState: "timeout_restore",
          updatedAt: now,
        });
        await writeJunctionEvent({
          ambulanceId: AMBULANCE_ID,
          eventType: "timeout_restore",
          junctionId: junction.junctionId,
          junctionName: junction.name,
          rfidTagId: RFID_TAG,
          timestamp: now,
        });
        toast(`Safety timeout issued at ${junction.name}`, "info");
      } else if (type === "manual_priority") {
        await writeJunction(junction.junctionId, {
          signalState: "priority_active",
          preemptionMode: "manual",
          updatedAt: now,
        });
        toast(`Manual priority held at ${junction.name}`, "success");
      } else {
        await writeJunction(junction.junctionId, {
          activeAmbulanceId: null,
          activeLane: null,
          preemptionMode: "none",
          signalState: "normal",
          updatedAt: now,
        });
        await writeJunctionEvent({
          ambulanceId: AMBULANCE_ID,
          eventType: "manual_reset",
          junctionId: junction.junctionId,
          junctionName: junction.name,
          rfidTagId: RFID_TAG,
          timestamp: now,
        });
        toast(`${junction.name} restored to normal cycle`, "success");
      }
    } catch (error) {
      toast(`Action failed: ${error.message}`, "error");
    } finally {
      setBusy(false);
    }
  }

  async function releaseCorridor(ambulance) {
    if (data.demo.mode) {
      toast("Demo scenario is active — release is disabled.", "info");
      return;
    }
    if (!window.confirm(`Release the corridor for ${ambulance.ambulanceId}?`)) return;
    setBusy(true);
    try {
      await writeAmbulanceStatus(ambulance.ambulanceId, {
        emergencyActive: false,
        status: "available",
        updatedAt: Date.now(),
      });
      await writeTrip(TRIP_ID, { status: "completed", endedAt: Date.now() });
      toast(`Corridor released for ${ambulance.ambulanceId}`, "success");
    } catch (error) {
      toast(`Release failed: ${error.message}`, "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleSeed(kind) {
    if (!data.firebase.online) {
      toast("Firebase is offline — cannot seed.", "error");
      return;
    }
    setBusy(true);
    try {
      if (kind === "hospitals") {
        await seedDemoHospitals();
        toast("Demo hospitals written to Firebase.", "success");
      } else {
        await seedDemoInfrastructure();
        toast("Demo users, vehicles and junctions written.", "success");
      }
    } catch (error) {
      toast(`Seed failed: ${error.message}`, "error");
    } finally {
      setBusy(false);
    }
  }

  const dataAge = data.lastDataAt != null ? ageSeconds(data.lastDataAt, data.now) : null;
  const live = !data.demo.mode && data.firebase.online;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-badge">
            <Siren size={22} />
          </div>
          <div>
            <div className="brand-title">SAPTCS</div>
            <div className="brand-sub">Control Room</div>
          </div>
        </div>

        <nav className="nav">
          {NAV.map((item) => {
            const Icon = item.icon;
            const badge = badgeFor(item.key);
            return (
              <button
                key={item.key}
                className={`nav-item ${view === item.key ? "active" : ""}`}
                onClick={() => setView(item.key)}
              >
                <Icon size={18} />
                {item.label}
                {badge ? <span className="nav-count">{badge}</span> : null}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <div className="link-status">
            <span className="label">Firebase</span>
            <Badge tone={live ? "success" : data.firebase.error ? "danger" : "amber"}>
              {data.demo.mode ? "demo" : data.firebase.online ? "live" : data.firebase.ready ? "offline" : "connecting"}
            </Badge>
          </div>
          <div className="link-status">
            <span className="label">MQTT</span>
            <Badge tone={data.mqtt.status === "live" ? "success" : data.mqtt.status === "connecting" ? "amber" : "danger"}>
              {data.mqtt.status}
            </Badge>
          </div>
          {dataAge != null ? (
            <div className="link-status">
              <span className="label">Data age</span>
              <span className="mono dim">{Math.floor(dataAge)}s</span>
            </div>
          ) : null}
          {operator ? (
            <div className="operator">
              <div className="label">Operator</div>
              <div className="operator-name mono" title={operator}>
                {operator}
              </div>
              <button className="btn btn-ghost btn-sm" onClick={onSignOut}>
                Sign out
              </button>
            </div>
          ) : null}
        </div>
      </aside>

      <div className="content">
        <header className="topbar">
          <div>
            <p className="eyebrow">Traffic Police Control Room</p>
            <h1>Smart Ambulance Priority Console</h1>
          </div>
          <div className="spacer" />
          <div className={`incident-banner ${emergencyCount ? "active" : ""}`}>
            {emergencyCount ? <Dot live /> : <Dot tone="green" />}
            {emergencyCount
              ? `${emergencyCount} emergency ${emergencyCount === 1 ? "corridor" : "corridors"} active`
              : "System standby"}
          </div>
          {data.demo.mode ? <Badge tone="amber"><Radio size={11} /> demo scenario</Badge> : null}
        </header>

        <Suspense fallback={<ViewFallback />}>
          {view === "operations" && (
            <OperationsView
              data={data}
              follow={follow}
              setFollow={setFollow}
              onRelease={releaseCorridor}
              onFocus={focusOn}
            />
          )}
          {view === "junctions" && (
            <JunctionsView data={data} onAction={handleJunctionAction} busy={busy || data.demo.mode} />
          )}
          {view === "fleet" && <FleetView data={data} onFocus={focusOn} />}
          {view === "hospitals" && <HospitalsView data={data} />}
          {view === "alerts" && <AlertsView data={data} />}
          {view === "system" && <SystemView data={data} onSeed={handleSeed} seeding={busy} />}
        </Suspense>
      </div>

      <nav className="mobile-nav">
        {NAV.map((item) => {
          const Icon = item.icon;
          const badge = badgeFor(item.key);
          return (
            <button
              key={item.key}
              className={view === item.key ? "active" : ""}
              onClick={() => setView(item.key)}
            >
              <span style={{ position: "relative" }}>
                <Icon size={19} />
                {badge ? (
                  <span
                    style={{
                      position: "absolute",
                      top: -5,
                      right: -8,
                      minWidth: 15,
                      height: 15,
                      borderRadius: 999,
                      background: "var(--red)",
                      color: "#fff",
                      fontSize: "0.58rem",
                      display: "grid",
                      placeItems: "center",
                      padding: "0 3px",
                    }}
                  >
                    {badge}
                  </span>
                ) : null}
              </span>
              {item.label}
            </button>
          );
        })}
      </nav>

      <div className="toasts">
        {toasts.map((item) => (
          <div key={item.id} className={`toast ${item.kind}`}>
            {item.kind === "error" ? <Bell size={16} /> : item.kind === "success" ? <Activity size={16} /> : <Radio size={16} />}
            <span>{item.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function AuthSplash() {
  return (
    <div className="login-shell">
      <div className="card login-card">
        <div className="brand">
          <div className="brand-badge">
            <Siren size={22} />
          </div>
          <div>
            <div className="brand-title">SAPTCS</div>
            <div className="brand-sub">Control Room</div>
          </div>
        </div>
        <div className="empty">
          <strong>Restoring session…</strong>
        </div>
      </div>
    </div>
  );
}

// Entry point. Live data requires an operator session because the Realtime
// Database rules require `auth != null`; the offline demo deliberately does
// not, so the console stays demonstrable with no credentials or network.
export default function App() {
  const demoMode = new URLSearchParams(window.location.search).get("demo") === "1";
  const { ready, user, operator, signOut } = useAuth();

  if (demoMode) return <Console operator={null} onSignOut={null} />;
  if (!ready) return <AuthSplash />;
  if (!user) return <LoginGate />;
  return <Console operator={operator} onSignOut={signOut} />;
}

