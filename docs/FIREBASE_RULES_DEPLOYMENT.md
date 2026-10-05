# Firebase Security Rules — Deployable Configuration

## Why this exists

The Realtime Database was **readable by anyone on the internet with no
authentication**, including `users`, which stores each account's `pin` in
plaintext. Verified against the live database:

```
GET /.json?shallow=true   -> 200   (entire tree enumerated)
GET /users.json           -> 200   (admin_001, police_001, driver_001, ...)
GET /users/driver_001.json-> 200   fields: active, ambulanceId, name, pin, role, userId
```

`docs/FIREBASE_SECURITY_RULES.md` describes an intended ruleset, but it was
never version-controlled, has no `firebase.json`, and — importantly — **cannot
be deployed as written** (see *Why the older document is not deployable* below).

## What is now in the repository

| File | Purpose |
| --- | --- |
| `database.rules.json` | The rules themselves. Strict JSON, no comments, so no parser can choke on it. |
| `firebase.json` | Points the Firebase CLI at that rules file. |
| `.firebaserc` | Binds the working copy to project `smart-ambulance-36f9d`. |
| `scripts/check-database-rules.mjs` | Fails the build if the rules regress or drift from the clients' schema. |

Run the checker any time:

```bash
node scripts/check-database-rules.mjs
```

It asserts four properties and also derives node coverage from
`FirebasePaths.kt`, the dashboard's `firebasePaths`, and the paths the ESP32
sketches patch — so adding a database node in code without a rule fails here
rather than silently falling through to the default deny at runtime.

## What the rules do

- **Default deny** at the root: nothing is granted implicitly.
- **Reads require `auth != null`** on every node — this is what closes the
  public read of `users` and everything else.
- **Writes require `auth != null`**, except for four locations the ESP32
  firmware writes with no credentials at all.
- Unauthenticated writes are deliberately **narrowed** from today's free-for-all:
  an anonymous client can update `ambulances/<id>/lastLocation`, but *not* the
  rest of the ambulance record (so it cannot flip `emergencyActive`).

## Prerequisites you must complete (only you can)

1. **Enable the Email/Password provider.** It is currently **disabled** —
   verified by attempting a sign-in, which returned
   `auth/configuration-not-found` and this message in the console:
   *"Email/password sign-in is not enabled for this Firebase project."*
   Enable it under **Authentication → Sign-in method → Email/Password**.
2. **Create the first operator account** under **Authentication → Users → Add
   user**, then share those credentials with control-room staff.
3. **Deploy the rules:**
   ```bash
   npx firebase-tools deploy --only database
   ```
   The CLI keeps a revision history, so the previous ruleset can be restored
   from the console's *Rules* tab if needed.

Until step 3 is done, the rules in this repo are inert — the live database is
still open.

## What will keep working

- **Android app** — it calls `signInAnonymously()` before any database
  access, so `auth != null` is satisfied.
- **ESP32 firmware** — its writes are preserved by the four carve-outs.
- **The dashboard** — once an operator signs in.

## Remaining risk (be honest about this)

- **Unauthenticated writes still exist** for `junctions/$junctionId`,
  `loraTelemetry`, `junctionEvents` and `ambulances/$ambulanceId/lastLocation`.
  Anyone who knows the URL can still spoof an ambulance position or a junction
  state. Closing this requires the firmware to authenticate, which means
  changing the sketches — deliberately out of scope here so the hardware keeps
  reporting.
- **`auth != null` is a weak barrier while anonymous auth stays enabled.**
  Anonymous sign-in is self-service, so a determined attacker can still obtain
  a token. Genuine security needs anonymous auth disabled plus real accounts on
  every client (Android included); this staging still removes public
  enumeration and protects the credentials.
- **PINs are still stored in plaintext** and are not used by Firebase Auth. They
  should be hashed or replaced with real credentials.

## Why the older document is not deployable

`docs/FIREBASE_SECURITY_RULES.md` keys role checks on
`root.child('users').child(auth.uid)`. That mapping does not exist:

- The Android app signs in **anonymously**, so `auth.uid` is an anonymous UID,
  never `driver_001`.
- The firmware holds **no credentials at all**.

Deploying that ruleset would lock out both the phone app and the hardware. It is
retained for reference, with a warning banner, and is superseded by
`database.rules.json`.
