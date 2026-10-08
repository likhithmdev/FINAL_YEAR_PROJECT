import { describe, expect, it } from "vitest";
import {
  OPERATOR_EMAIL_DOMAIN,
  PIN_PREFIX,
  emailForOperator,
  passwordForOperator,
} from "./operatorIdentity";

// These assertions deliberately mirror OperatorIdentityTest.kt in the Android
// app. The two clients must accept identical input, so both suites pin the same
// contract. Getting the mapping wrong locks every operator out; getting it too
// loose would let an unexpected address be tried.

describe("operator email mapping", () => {
  it("keeps the synthetic domain stable", () => {
    // Changing this locks out every existing account, so it is pinned here.
    expect(OPERATOR_EMAIL_DOMAIN).toBe("saptcs.local");
  });

  it("turns a bare operator id into a synthetic address", () => {
    expect(emailForOperator("driver_001")).toBe("driver_001@saptcs.local");
    expect(emailForOperator("police_001")).toBe("police_001@saptcs.local");
  });

  it("normalises an id to lower case", () => {
    expect(emailForOperator("Driver_001")).toBe("driver_001@saptcs.local");
    expect(emailForOperator("ADMIN_001")).toBe("admin_001@saptcs.local");
  });

  it("ignores surrounding whitespace", () => {
    expect(emailForOperator("  driver_001  ")).toBe("driver_001@saptcs.local");
  });

  it("passes a full address through so existing accounts still work", () => {
    expect(emailForOperator("ops@example.com")).toBe("ops@example.com");
    expect(emailForOperator("driver_001@saptcs.local")).toBe("driver_001@saptcs.local");
  });

  it("maps empty input to an empty local part rather than a valid address", () => {
    // Matches the Kotlin behaviour exactly; the form rejects empty input first.
    expect(emailForOperator("")).toBe("@saptcs.local");
  });
});

describe("operator pin expansion", () => {
  it("keeps the pin prefix stable", () => {
    // The live accounts are stored with exactly this prefix, so changing it
    // locks out every operator until they are re-issued.
    expect(PIN_PREFIX).toBe("saptcs");
  });

  it("expands a four-digit pin to the stored password", () => {
    // Deliberately not the live operator PINs: this file is committed and
    // published, and the live PINs are the accounts' only secret. Any four
    // digits exercise exactly the same branch.
    expect(passwordForOperator("4321")).toBe("saptcs4321");
    expect(passwordForOperator("8765")).toBe("saptcs8765");
    expect(passwordForOperator("2468")).toBe("saptcs2468");
    expect(passwordForOperator("1357")).toBe("saptcs1357");
    expect(passwordForOperator("9090")).toBe("saptcs9090");
  });

  it("ignores whitespace around a pin", () => {
    expect(passwordForOperator("  8765  ")).toBe("saptcs8765");
  });

  it("passes a normal password through untouched", () => {
    // Accounts created outside the PIN convention must still sign in.
    expect(passwordForOperator("admin123")).toBe("admin123");
    expect(passwordForOperator("S3cret!Pass")).toBe("S3cret!Pass");
    expect(passwordForOperator("saptcs")).toBe("saptcs");
  });

  it("treats only exactly four digits as a pin", () => {
    // Other numeric lengths are never silently rewritten.
    expect(passwordForOperator("123")).toBe("123");
    expect(passwordForOperator("12345")).toBe("12345");
    expect(passwordForOperator("123456")).toBe("123456");
  });

  it("does not expand a four character non-numeric secret", () => {
    expect(passwordForOperator("abcd")).toBe("abcd");
    expect(passwordForOperator("12a4")).toBe("12a4");
  });

  it("leaves an empty secret empty", () => {
    expect(passwordForOperator("")).toBe("");
    expect(passwordForOperator("   ")).toBe("");
  });
});
