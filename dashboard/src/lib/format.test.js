import { describe, expect, it } from "vitest";
import {
  ageSeconds,
  formatAge,
  formatDistance,
  formatDuration,
  formatEta,
  timestampMs,
  titleCase,
} from "./format";

describe("formatDistance", () => {
  it("uses metres below a kilometre and kilometres above", () => {
    expect(formatDistance(0)).toBe("0 m");
    expect(formatDistance(500)).toBe("500 m");
    expect(formatDistance(999)).toBe("999 m");
    expect(formatDistance(1000)).toBe("1.0 km");
    expect(formatDistance(1500)).toBe("1.5 km");
    expect(formatDistance(12345)).toBe("12.3 km");
  });

  it("shows a placeholder for missing values", () => {
    expect(formatDistance(null)).toBe("--");
    expect(formatDistance(undefined)).toBe("--");
    expect(formatDistance(NaN)).toBe("--");
  });
});

describe("formatEta", () => {
  it("rounds to whole minutes", () => {
    expect(formatEta(59)).toBe("1 min");
    expect(formatEta(60)).toBe("1 min");
    expect(formatEta(90)).toBe("2 min");
    expect(formatEta(600)).toBe("10 min");
  });

  it("describes a sub-minute ETA without claiming zero", () => {
    expect(formatEta(0)).toBe("<1 min");
    expect(formatEta(20)).toBe("<1 min");
  });

  it("never goes negative and handles missing values", () => {
    expect(formatEta(-30)).toBe("<1 min");
    expect(formatEta(null)).toBe("--");
    expect(formatEta(NaN)).toBe("--");
  });
});

describe("formatDuration", () => {
  it("formats as mm:ss under an hour and h:mm:ss above", () => {
    expect(formatDuration(0)).toBe("00:00");
    expect(formatDuration(1000)).toBe("00:01");
    expect(formatDuration(65000)).toBe("01:05");
    expect(formatDuration(3599000)).toBe("59:59");
    expect(formatDuration(3600000)).toBe("1:00:00");
    expect(formatDuration(3661000)).toBe("1:01:01");
  });

  it("rejects negative or missing durations", () => {
    expect(formatDuration(-1)).toBe("--");
    expect(formatDuration(null)).toBe("--");
    expect(formatDuration(NaN)).toBe("--");
  });
});

describe("formatAge", () => {
  it("scales from seconds to hours", () => {
    expect(formatAge(0)).toBe("just now");
    expect(formatAge(4.9)).toBe("just now");
    expect(formatAge(5)).toBe("5s ago");
    expect(formatAge(59)).toBe("59s ago");
    expect(formatAge(60)).toBe("1m ago");
    expect(formatAge(3599)).toBe("59m ago");
    expect(formatAge(3600)).toBe("1h ago");
    expect(formatAge(7200)).toBe("2h ago");
  });

  it("reports no data when the age is unknown", () => {
    expect(formatAge(null)).toBe("no data");
    expect(formatAge(NaN)).toBe("no data");
  });
});

describe("timestampMs", () => {
  it("passes through epoch numbers", () => {
    expect(timestampMs(1750000000000)).toBe(1750000000000);
  });

  it("parses ISO strings", () => {
    const iso = "2026-10-04T00:00:00.000Z";
    expect(timestampMs(iso)).toBe(Date.parse(iso));
  });

  it("handles the { seconds } shape Firebase serialises server timestamps into", () => {
    expect(timestampMs({ seconds: 1000 })).toBe(1000000);
  });

  it("returns null for anything it cannot interpret", () => {
    expect(timestampMs(null)).toBeNull();
    expect(timestampMs(undefined)).toBeNull();
    expect(timestampMs("not a date")).toBeNull();
    expect(timestampMs({})).toBeNull();
    expect(timestampMs(true)).toBeNull();
  });
});

describe("ageSeconds", () => {
  it("measures the gap in seconds", () => {
    expect(ageSeconds(1000, 5000)).toBe(4);
  });

  it("clamps future timestamps to zero rather than going negative", () => {
    // Clock skew between the ESP32 and the browser would otherwise render
    // "updated -4s ago".
    expect(ageSeconds(5000, 1000)).toBe(0);
  });

  it("returns null when the timestamp is unusable", () => {
    expect(ageSeconds(null, 1000)).toBeNull();
    expect(ageSeconds("nope", 1000)).toBeNull();
  });
});

describe("titleCase", () => {
  it("turns snake and kebab case into words", () => {
    expect(titleCase("gps_preempt_started")).toBe("Gps Preempt Started");
    expect(titleCase("hospital-alert")).toBe("Hospital Alert");
    expect(titleCase("Critical")).toBe("Critical");
  });

  it("returns an empty string for non-strings", () => {
    expect(titleCase("")).toBe("");
    expect(titleCase(null)).toBe("");
    expect(titleCase(123)).toBe("");
  });
});
