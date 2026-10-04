// Geospatial helpers shared by the live map and corridor calculations.
// Mirrors the Haversine/bearing logic used by the junction firmware so the
// dashboard numbers match what the ESP32 traffic controller decides on.

const EARTH_RADIUS_M = 6371000;

const toRad = (deg) => (deg * Math.PI) / 180;
const toDeg = (rad) => (rad * 180) / Math.PI;

export function isValidCoord(lat, lng) {
  return (
    typeof lat === "number" &&
    typeof lng === "number" &&
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    Math.abs(lat) <= 90 &&
    Math.abs(lng) <= 180
  );
}

// Great-circle distance in metres.
export function haversineMeters(from, to) {
  if (!from || !to) return null;
  if (!isValidCoord(from.lat, from.lng) || !isValidCoord(to.lat, to.lng)) return null;
  const dLat = toRad(to.lat - from.lat);
  const dLng = toRad(to.lng - from.lng);
  const lat1 = toRad(from.lat);
  const lat2 = toRad(to.lat);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
}

// Initial bearing in degrees (0-360) from `from` to `to`.
export function bearingDeg(from, to) {
  if (!from || !to) return null;
  if (!isValidCoord(from.lat, from.lng) || !isValidCoord(to.lat, to.lng)) return null;
  const lat1 = toRad(from.lat);
  const lat2 = toRad(to.lat);
  const dLng = toRad(to.lng - from.lng);
  const y = Math.sin(dLng) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

export function compassLabel(deg) {
  if (deg == null || Number.isNaN(deg)) return null;
  const points = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  return points[Math.round(deg / 45) % 8];
}

// Estimated seconds to travel `meters` at `speedKmph`. Falls back to a
// conservative urban response speed when the live speed is missing or idle,
// so a standstill ambulance still yields a usable ETA for hospital alerts.
export function etaSeconds(meters, speedKmph) {
  if (meters == null || !Number.isFinite(meters)) return null;
  const speed = Number.isFinite(speedKmph) && speedKmph > 5 ? speedKmph : 30;
  return (meters / 1000 / speed) * 3600;
}
