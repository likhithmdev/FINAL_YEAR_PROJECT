import React, { useEffect, useMemo, useRef } from "react";
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { Navigation, Siren } from "lucide-react";
import { compassLabel } from "../lib/geo";
import { formatDistance, formatEta } from "../lib/format";

// Local, dependency-free markers so the console renders correctly on an
// air-gapped demo laptop (no CDN icon fetches).
function makeIcon({ color, glyph, size = 34 }) {
  return L.divIcon({
    className: "",
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
    html: `<div style="
      width:${size}px;height:${size}px;border-radius:50%;
      display:grid;place-items:center;
      background:${color}22;border:2px solid ${color};
      color:${color};font-size:14px;font-weight:700;
      box-shadow:0 0 0 4px ${color}1f;">${glyph}</div>`,
  });
}

const ICONS = {
  ambulance: makeIcon({ color: "#ef233c", glyph: "&#128657;", size: 38 }),
  junction: makeIcon({ color: "#3b82f6", glyph: "&#9888;", size: 30 }),
  junctionPriority: makeIcon({ color: "#10b981", glyph: "&#9888;", size: 32 }),
  junctionOffline: makeIcon({ color: "#3d4f6e", glyph: "&#9888;", size: 28 }),
  hospital: makeIcon({ color: "#10b981", glyph: "&#10010;", size: 30 }),
  destination: makeIcon({ color: "#f59e0b", glyph: "&#10010;", size: 34 }),
};

function FitBounds({ points, fitKey }) {
  const map = useMap();
  const lastKey = useRef(null);
  useEffect(() => {
    if (!points.length) return;
    if (lastKey.current === fitKey) return;
    lastKey.current = fitKey;
    if (points.length === 1) {
      map.setView(points[0], 15, { animate: false });
    } else {
      map.fitBounds(L.latLngBounds(points).pad(0.25), { animate: false });
    }
  }, [points, fitKey, map]);
  return null;
}

function Follow({ position, enabled }) {
  const map = useMap();
  useEffect(() => {
    if (enabled && position) map.setView(position, Math.max(map.getZoom(), 15), { animate: true });
  }, [enabled, position, map]);
  return null;
}

// One-shot fly-to triggered by the control room ("Locate", "Centre map").
function FocusTo({ target }) {
  const map = useMap();
  const nonce = target?.nonce;
  useEffect(() => {
    if (nonce == null) return;
    if (typeof target.lat === "number" && typeof target.lng === "number") {
      map.setView([target.lat, target.lng], Math.max(map.getZoom(), 16), { animate: true });
    }
  }, [nonce, target?.lat, target?.lng, map]);
  return null;
}

export default function MapPanel({ corridors = [], junctions = [], hospitals = [], follow = false, focusTarget = null }) {
  const trackPoints = useMemo(() => {
    const points = [];
    corridors.forEach((corridor) => {
      if (corridor.position) points.push([corridor.position.lat, corridor.position.lng]);
    });
    return points;
  }, [corridors]);

  const fitPoints = useMemo(() => {
    const points = [...trackPoints];
    junctions.forEach((junction) => junction.location && points.push([junction.location.lat, junction.location.lng]));
    hospitals.forEach((hospital) => hospital.location && points.push([hospital.location.lat, hospital.location.lng]));
    return points;
  }, [trackPoints, junctions, hospitals]);

  const fitKey = `${trackPoints.length}:${junctions.length}:${hospitals.filter((h) => h.location).length}`;
  const destinationIds = new Set(corridors.map((c) => c.ambulance?.destinationHospitalId).filter(Boolean));
  const center = trackPoints[0] || (junctions.find((j) => j.location)?.location ? [junctions.find((j) => j.location).location.lat, junctions.find((j) => j.location).location.lng] : [12.9716, 77.5946]);

  return (
    <div className="map-wrap">
      <MapContainer center={center} zoom={14} style={{ height: "100%", width: "100%" }} preferCanvas scrollWheelZoom>
        <TileLayer
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        <FitBounds points={fitPoints} fitKey={fitKey} />
        <Follow position={trackPoints[0]} enabled={follow} />
        <FocusTo target={focusTarget} />

        {corridors.map((corridor) => {
          const position = corridor.position;
          if (!position) return null;
          const line = [
            [position.lat, position.lng],
            corridor.nearestJunction?.location
              ? [corridor.nearestJunction.location.lat, corridor.nearestJunction.location.lng]
              : null,
            corridor.destination?.location
              ? [corridor.destination.location.lat, corridor.destination.location.lng]
              : null,
          ].filter(Boolean);
          return (
            <React.Fragment key={corridor.ambulance.ambulanceId}>
              {line.length > 1 && (
                <Polyline positions={line} pathOptions={{ color: "#ef233c", weight: 3, opacity: 0.75, dashArray: "8 8" }} />
              )}
              <Marker position={[position.lat, position.lng]} icon={ICONS.ambulance}>
                <Popup>
                  <strong>Ambulance {corridor.ambulance.ambulanceId}</strong>
                  {corridor.ambulance.severity} · {corridor.ambulance.status}
                  <br />
                  Speed: {corridor.speed ?? "--"} km/h
                  {corridor.headingDeg != null ? ` · Heading ${compassLabel(corridor.headingDeg) || corridor.headingDeg}` : ""}
                  <br />
                  To hospital: {formatDistance(corridor.hospitalMeters)} · {formatEta(corridor.hospitalEta)}
                  <br />
                  Nearest junction: {corridor.nearestJunction?.name || "--"} ({formatDistance(corridor.nearestJunctionMeters)})
                </Popup>
              </Marker>
            </React.Fragment>
          );
        })}

        {junctions.map((junction) => {
          if (!junction.location) return null;
          const icon = !junction.online
            ? ICONS.junctionOffline
            : junction.signalState === "priority_active"
              ? ICONS.junctionPriority
              : ICONS.junction;
          return (
            <Marker key={junction.junctionId} position={[junction.location.lat, junction.location.lng]} icon={icon}>
              <Popup>
                <strong>{junction.name}</strong>
                {junction.junctionId} · {junction.lane || "lane --"}
                <br />
                Signal: {junction.signalState} · {junction.preemptionMode}
                <br />
                {junction.distanceMeters != null ? `Ambulance ${formatDistance(junction.distanceMeters)} away` : "No active approach"}
                {junction.rssi != null ? ` · RSSI ${junction.rssi} dBm` : ""}
                <br />
                <span style={{ color: junction.online ? "#6ee7b7" : "#ff97a3" }}>
                  {junction.online ? "ESP32 online" : "No recent heartbeat"}
                </span>
              </Popup>
            </Marker>
          );
        })}

        {hospitals.map((hospital) => {
          if (!hospital.location) return null;
          const isDestination = destinationIds.has(hospital.hospitalId);
          return (
            <Marker
              key={hospital.hospitalId}
              position={[hospital.location.lat, hospital.location.lng]}
              icon={isDestination ? ICONS.destination : ICONS.hospital}
            >
              <Popup>
                <strong>{isDestination ? "Destination · " : ""}{hospital.name}</strong>
                {hospital.hospitalId}
                <br />
                Beds: {hospital.bedsAvailable ?? "--"} · {hospital.emergencyAvailable ? "Emergency open" : "Emergency full"}
                {hospital.phone ? <><br />{hospital.phone}</> : null}
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>

      <div className="map-overlay tl">
        <Siren size={14} /> {corridors.length} active {corridors.length === 1 ? "ambulance" : "ambulances"}
      </div>
      <div className="map-overlay tr">
        <Navigation size={14} /> {follow ? "Following AMB" : "Corridor view"}
      </div>
    </div>
  );
}
