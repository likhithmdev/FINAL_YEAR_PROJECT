// Small presentation helpers used across the operations console.

export function formatDistance(meters) {
  if (meters == null || !Number.isFinite(meters)) return "--";
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(1)} km`;
}

export function formatEta(seconds) {
  if (seconds == null || !Number.isFinite(seconds)) return "--";
  const mins = Math.max(0, Math.round(seconds / 60));
  if (mins < 1) return "<1 min";
  return `${mins} min`;
}

export function formatClock(value = Date.now()) {
  const ts = timestampMs(value) ?? Date.now();
  return new Date(ts).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function formatDateTime(value) {
  const ts = timestampMs(value);
  if (ts == null) return "--";
  return new Date(ts).toLocaleString([], {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDuration(ms) {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return "--";
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function formatAge(seconds) {
  if (seconds == null || !Number.isFinite(seconds)) return "no data";
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${Math.floor(seconds)}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  return `${Math.floor(seconds / 3600)}h ago`;
}

// Tolerant timestamp parser: handles epoch numbers, ISO strings and the
// { seconds } shape Firebase sometimes serialises server timestamps into.
export function timestampMs(value) {
  if (value == null) return null;
  if (typeof value === "number") return value;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? null : parsed;
  }
  if (typeof value === "object" && typeof value.seconds === "number") {
    return value.seconds * 1000;
  }
  return null;
}

export function ageSeconds(value, nowMs = Date.now()) {
  const ts = timestampMs(value);
  if (ts == null) return null;
  return Math.max(0, (nowMs - ts) / 1000);
}

export function titleCase(value) {
  if (typeof value !== "string" || !value) return "";
  return value
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
