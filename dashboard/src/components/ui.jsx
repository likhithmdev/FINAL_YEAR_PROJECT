import React from "react";

export function Badge({ tone = "neutral", children, className = "" }) {
  return <span className={`badge badge-${tone} ${className}`}>{children}</span>;
}

export function Dot({ tone = "neutral", live = false }) {
  if (live) return <span className="dot dot-live" />;
  return <span className={tone === "neutral" ? "dot" : `dot dot-${tone}`} />;
}

export function Stat({ icon: Icon, value, label, detail, tone = "blue" }) {
  return (
    <article className={`stat tone-${tone}`}>
      {Icon ? (
        <div className="icon">
          <Icon size={20} />
        </div>
      ) : null}
      <div>
        <div className="value">{value}</div>
        <div className="label">{label}</div>
        {detail ? <div className="detail">{detail}</div> : null}
      </div>
    </article>
  );
}

export function Panel({ title, icon: Icon, hint, actions, className = "", children }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-head">
          <h3>
            {Icon ? <Icon size={17} /> : null}
            {title}
            {hint ? <span className="hint">· {hint}</span> : null}
          </h3>
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function Empty({ icon: Icon, title, detail }) {
  return (
    <div className="empty">
      {Icon ? <Icon size={26} /> : null}
      <strong>{title}</strong>
      {detail ? <span>{detail}</span> : null}
    </div>
  );
}

export function Toggle({ on, onChange, label, disabled = false }) {
  return (
    <label className="toggle" style={{ cursor: disabled ? "not-allowed" : "pointer" }}>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-label={label}
        disabled={disabled}
        className={`switch ${on ? "on" : ""}`}
        onClick={() => !disabled && onChange?.(!on)}
      />
      {label}
    </label>
  );
}

export function KeyValue({ items }) {
  return (
    <div className="kv">
      {items.map((item) => (
        <div key={item.k}>
          <div className="k">{item.k}</div>
          <div className="v">{item.v}</div>
        </div>
      ))}
    </div>
  );
}

export function SignalDots({ state, offline = false }) {
  // Green when priority/preemption, amber for timeout restore, otherwise the
  // normal red cycle. Offline junctions show all lamps unlit.
  const red = !offline && state !== "priority_active" && state !== "timeout_restore" ? "on-red" : "";
  const amber = !offline && state === "timeout_restore" ? "on-amber" : "";
  const green = !offline && state === "priority_active" ? "on-green" : "";
  return (
    <div className="signal-dots" aria-label={`Signal ${state}`}>
      <span className={red} />
      <span className={amber} />
      <span className={green} />
    </div>
  );
}

export function signalTone(signalState) {
  if (signalState === "priority_active") return "success";
  if (signalState === "timeout_restore") return "amber";
  return "neutral";
}

export function signalLabel(signalState) {
  if (signalState === "priority_active") return "Priority active";
  if (signalState === "timeout_restore") return "Timeout restore";
  return "Normal cycle";
}

export function preemptionLabel(mode) {
  switch (mode) {
    case "gps_lora":
      return "GPS + LoRa";
    case "rssi_fallback":
      return "RSSI fallback";
    case "rfid_clearance":
      return "RFID clearance";
    case "manual":
      return "Manual override";
    default:
      return "None";
  }
}
