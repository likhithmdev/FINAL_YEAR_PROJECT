import { describe, expect, it } from "vitest";
import { bearingDeg, compassLabel, etaSeconds, haversineMeters, isValidCoord } from "./geo";

// These helpers must agree with the firmware's own Haversine/bearing maths,
// otherwise the console would report different distances than the ESP32 uses
// to decide preemption.

describe("isValidCoord", () => {
  it("accepts real coordinates", () => {
    expect(isValidCoord(12.9716, 77.5946)).toBe(true);
    expect(isValidCoord(0, 0)).toBe(true);
    expect(isValidCoord(-90, -180)).toBe(true);
  });

  it("rejects out-of-range, non-finite and non-numeric values", () => {
    expect(isValidCoord(90.1, 0)).toBe(false);
    expect(isValidCoord(-90.1, 0)).toBe(false);
    expect(isValidCoord(0, 180.1)).toBe(false);
    expect(isValidCoord(NaN, 0)).toBe(false);
    expect(isValidCoord(Infinity, 0)).toBe(false);
    expect(isValidCoord("12.9716", 77.5946)).toBe(false);
    expect(isValidCoord(undefined, 77.5946)).toBe(false);
  });
});

describe("haversineMeters", () => {
  it("returns zero for the same point", () => {
    expect(haversineMeters({ lat: 12.9716, lng: 77.5946 }, { lat: 12.9716, lng: 77.5946 })).toBe(0);
  });

  it("matches the known length of one degree of latitude", () => {
    // One degree of latitude is ~111.195 km on a sphere of radius 6371 km.
    const meters = haversineMeters({ lat: 0, lng: 0 }, { lat: 1, lng: 0 });
    expect(meters).toBeGreaterThan(111000);
    expect(meters).toBeLessThan(111400);
  });

  it("is symmetric", () => {
    const a = { lat: 12.9772, lng: 77.5875 };
    const b = { lat: 12.9716, lng: 77.5946 };
    expect(haversineMeters(a, b)).toBeCloseTo(haversineMeters(b, a), 6);
  });

  it("returns null for missing or invalid input", () => {
    expect(haversineMeters(null, { lat: 1, lng: 1 })).toBeNull();
    expect(haversineMeters({ lat: 1, lng: 1 }, undefined)).toBeNull();
    expect(haversineMeters({ lat: 91, lng: 0 }, { lat: 1, lng: 1 })).toBeNull();
    expect(haversineMeters({ lat: "1", lng: 1 }, { lat: 1, lng: 1 })).toBeNull();
  });
});

describe("bearingDeg", () => {
  it("computes the four cardinal directions", () => {
    expect(bearingDeg({ lat: 0, lng: 0 }, { lat: 1, lng: 0 })).toBeCloseTo(0, 5);
    expect(bearingDeg({ lat: 0, lng: 0 }, { lat: 0, lng: 1 })).toBeCloseTo(90, 5);
    expect(bearingDeg({ lat: 1, lng: 0 }, { lat: 0, lng: 0 })).toBeCloseTo(180, 5);
    expect(bearingDeg({ lat: 0, lng: 1 }, { lat: 0, lng: 0 })).toBeCloseTo(270, 5);
  });

  it("always reports 0-360", () => {
    const deg = bearingDeg({ lat: 0, lng: 1 }, { lat: 0, lng: 0 });
    expect(deg).toBeGreaterThanOrEqual(0);
    expect(deg).toBeLessThan(360);
  });

  it("returns null for invalid input", () => {
    expect(bearingDeg(null, { lat: 1, lng: 1 })).toBeNull();
    expect(bearingDeg({ lat: 0, lng: 0 }, { lat: 0, lng: 200 })).toBeNull();
  });
});

describe("compassLabel", () => {
  it("maps the eight compass points", () => {
    expect(compassLabel(0)).toBe("N");
    expect(compassLabel(45)).toBe("NE");
    expect(compassLabel(90)).toBe("E");
    expect(compassLabel(135)).toBe("SE");
    expect(compassLabel(180)).toBe("S");
    expect(compassLabel(225)).toBe("SW");
    expect(compassLabel(270)).toBe("W");
    expect(compassLabel(315)).toBe("NW");
  });

  it("wraps angles near 360 back to north", () => {
    expect(compassLabel(359)).toBe("N");
    expect(compassLabel(360)).toBe("N");
  });

  it("returns null for missing input", () => {
    expect(compassLabel(null)).toBeNull();
    expect(compassLabel(NaN)).toBeNull();
  });
});

describe("etaSeconds", () => {
  it("converts distance and speed into seconds", () => {
    expect(etaSeconds(1000, 60)).toBeCloseTo(60, 6);
    expect(etaSeconds(1000, 6)).toBeCloseTo(600, 6);
  });

  it("falls back to a conservative urban speed when idle or unknown", () => {
    // A standstill ambulance must still yield a usable ETA for hospital alerts.
    expect(etaSeconds(1000, null)).toBeCloseTo(120, 6);
    expect(etaSeconds(1000, 0)).toBeCloseTo(120, 6);
    expect(etaSeconds(1000, 5)).toBeCloseTo(120, 6);
  });

  it("returns null when distance is unusable", () => {
    expect(etaSeconds(null, 40)).toBeNull();
    expect(etaSeconds(NaN, 40)).toBeNull();
  });

  it("returns zero for zero distance", () => {
    expect(etaSeconds(0, 40)).toBe(0);
  });
});
