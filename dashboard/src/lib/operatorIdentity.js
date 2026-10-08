// Operators sign in with a bare ID and a four-digit PIN, but Firebase Auth needs
// a full email address and rejects passwords shorter than six characters. This
// module is the single mapping between the two.
//
// It deliberately mirrors `emailFor` / `passwordFor` in the Android app's
// SaptcsRepository so the phone and the control room accept identical input; if
// you change one, change the other or the two clients drift apart. Kept free of
// Firebase imports so it stays unit-testable without a browser or network.

/** Domain the synthetic operator addresses live on. Changing it locks out every account. */
export const OPERATOR_EMAIL_DOMAIN = "saptcs.local";

/** Prefix that turns a four-digit PIN into a password Auth will accept. */
export const PIN_PREFIX = "saptcs";

/**
 * Turns an operator ID (or a full email) into the Auth account address, so
 * `driver_001` authenticates as `driver_001@saptcs.local`. A full address is
 * passed through so accounts created outside this convention still work.
 */
export function emailForOperator(input) {
  const trimmed = String(input ?? "").trim();
  if (trimmed.includes("@")) return trimmed.toLowerCase();
  // Mirrors the Kotlin implementation, which also appends the domain to an
  // empty string rather than returning "". Callers reject empty input first.
  return `${trimmed.toLowerCase()}@${OPERATOR_EMAIL_DOMAIN}`;
}

/**
 * Expands a typed PIN into the password that Auth actually holds.
 *
 * Only exactly four digits count as a PIN; anything else is passed through
 * untouched, so a full password — or an account whose password is numeric but
 * not four digits long — is never silently rewritten.
 */
export function passwordForOperator(secret) {
  const trimmed = String(secret ?? "").trim();
  return /^\d{4}$/.test(trimmed) ? `${PIN_PREFIX}${trimmed}` : trimmed;
}
