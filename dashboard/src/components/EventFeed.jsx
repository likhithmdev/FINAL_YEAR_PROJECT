import React from "react";
import { Activity } from "lucide-react";
import { eventLabel, eventTone } from "../lib/model";
import { formatClock, formatDistance, titleCase } from "../lib/format";
import { Empty, Badge } from "./ui";

const TONE_CLASS = {
  danger: "dot-red",
  amber: "dot-amber",
  success: "dot-green",
  info: "dot-blue",
  neutral: "",
};

function describe(event) {
  const place = event.junctionName || event.junctionId || "junction";
  switch (event.eventType) {
    case "gps_preempt_started":
      return `GPS + LoRa preemption granted at ${place}${event.distanceMeters != null ? ` (${formatDistance(event.distanceMeters)})` : ""}`;
    case "rssi_preempt_started":
      return `RSSI fallback preemption at ${place}${event.rssi != null ? ` (${event.rssi} dBm)` : ""}`;
    case "rfid_clearance":
      return `Stop-line RFID cleared at ${place}${event.rfidTagId ? ` · ${event.rfidTagId}` : ""}`;
    case "timeout_restore":
      return `Safety timeout restored normal cycle at ${place}`;
    case "manual_reset":
      return `Control room reset signal at ${place}`;
    case "approach_tracking":
      return `Tracking ambulance approach at ${place}${event.distanceMeters != null ? ` (${formatDistance(event.distanceMeters)})` : ""}`;
    case "lora_gps_packet":
      return `LoRa GPS packet received at ${place}`;
    case "entry":
      return `Ambulance entered corridor at ${place}`;
    case "exit":
      return `Ambulance exited corridor at ${place}`;
    default:
      return `${titleCase(event.eventType || "Event")} at ${place}`;
  }
}

export default function EventFeed({ events = [], limit, compact = false }) {
  const list = limit ? events.slice(0, limit) : events;
  if (!list.length) {
    return <Empty icon={Activity} title="No junction events yet" detail="Events appear here as LoRa, RFID and MQTT activity arrives." />;
  }
  return (
    <div className="event-feed">
      {list.map((event) => (
        <div className="event" key={event.id}>
          <span className={`dot ${TONE_CLASS[eventTone(event.eventType)] || ""}`} />
          <div>
            <div className={`message`}>{describe(event)}</div>
            {!compact && (
              <div className="meta">
                {event.ambulanceId || "system"} · {event.source}
                {event.preemptionMode ? ` · ${event.preemptionMode}` : ""}
              </div>
            )}
          </div>
          <div className="when">{formatClock(event.timestamp)}</div>
        </div>
      ))}
    </div>
  );
}

export function EventToneBadge({ eventType }) {
  return <Badge tone={eventTone(eventType)}>{eventLabel(eventType)}</Badge>;
}
