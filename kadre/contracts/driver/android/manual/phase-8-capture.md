# Android Phase 8 — capture, consent and frames manual charter

This is a short manual complement to the deterministic Phase 8 emulator evidence
(`AND-009`). Its procedures cover the consent-mode and device frontiers the
headless API 35 emulator run cannot honestly stage: the WHOLE-DISPLAY consent
mode (the emulator delivers "A single app" — its spinner is not reachable
through the accessibility tree, so the automated run streams the single app the
consent selects, the self-record shape), a real mid-stream system revocation
(the cast tile's "Stop"), a real rotation mid-stream, a `FLAG_SECURE` app
inside the captured region, and the multi-window shapes. It is informative; it
creates no validator evidence, it does not change any contract status, and it
is not wired into the contract validator. It never replaces an automated proof,
and nothing in it may be read as the capability register: the register of this
adapter is [capabilities/android.md](../../../../capabilities/android.md)
(§2, §4).

**The decisions this charter operates under.** The three capture targets are
decided: `screen`/`window` (`HostChoice`) and `surface` are **Supported** (the
real MediaProjection path — system consent per session, single-use token, the
stateful `Denied(canRequestAgain = true)` refusal never a failure, the system
revocation the terminal `Stopped(PermissionRevoked)`; the real `PixelCopy` of
the attached SurfaceView, API 26+), and `Source`/`sourceEnumeration` are the
**recorded structural refusals** (nothing on Android enumerates capturable
surfaces before a consent — `HostPickerOnly`, `refreshSources` the honest
no-op; the web precedent). The SERVICE RESPONSIBILITY DECISION IS CLOSED (the
ruling P8-0): the `mediaProjection` foreground service is KADRE'S
responsibility — the library declares it in its own manifest, starts it before
`getMediaProjection`, stops it at the session terminal and posts the minimal
"Screen capture" notification — **the host declares nothing**, and the
documented consequence is that the notification appears during HostChoice
sessions. What the emulator cannot stage is the CONSENT MODE and the DEVICE
PHYSICS: the proof engine `Kadre_API_35` delivers the consent in "A single app"
mode, has no rotation sensor event worth the name, and cannot show a
second-app `FLAG_SECURE` window inside a mirrored region — the deterministic
evidence pins the machinery those facts exercise
(`resizeRepublishesReconfigureBeforeFrame` for the reconfigure path,
`serviceStartedBeforeVirtualDisplayAndStoppedAtTerminal` for the revoke
terminal) and the charter records what real hardware adds. Per `TEST-STRATEGY.md`
the material guarantees pass on the DECLARED device pool
(« le pool de devices déclaré supporté ») once one is qualified — until then,
these procedures are the record of what to run.

## Scope and prerequisites

Phase 8 covers the capture manager of an attached session — both attach forms
carry it (the consent registration is legal after RESUMED through the direct
registry, proven on device by `android-capture-late-attach-consent`). The rules
the procedures check are the ones the host suites (`AndroidCapturePortTest`,
`AndroidCaptureForegroundTest`) and `AND-009` already pin; what this charter
adds is the consent-mode and hardware physics no emulator stages.

| Prerequisite | Why |
| --- | --- |
| A real device (record manufacturer, model, Android version) | the whole-display consent spinner, the cast tile, the rotation sensor and cross-app `FLAG_SECURE` windows are the points; the emulator delivers none of them in exercisable form |
| A second app with `FLAG_SECURE` set (bank apps, or `adb shell am start` an activity after `window.setFlags(FLAG_SECURE, …)`) | procedure 4 needs protected content inside the captured region |
| A host app calling the public `attachKadre` — the contract consumer `kadre/consumers/android` is the ready-made one | every reading below is a fact a consumer of the public API can read |
| A debug dump of the public state — log `scope.capture.state.value` (capabilities, permission states, sources), the capture session's `state.value` (configuration, `Streaming`, terminations), every frame's size and revision, and the notification state | the observation surface; Kadre never prints on its own |
| The gate green first: `scripts/test-kadre-android-contracts.sh` (44 instrumented tests, nine JSONs, `AND-001`…`AND-009`) | the deterministic contract this charter complements |

Run the gate before the manual pass. The procedures may reveal an
engine-specific or device-specific issue, but may not be used to fabricate
contract evidence or to claim that an automated branch has passed.

## Procedures

### 1. The whole-display consent — the mode the emulator cannot reach

The API 35 emulator delivers the consent dialog in "A single app" mode: its
whole-display spinner is not reachable through the accessibility tree, so the
automated run streams the single app the consent selects (the self-record
shape — the instrumentation IS the projected app in the stream scenario). The
contractual facts do not depend on the mode; a WHOLE-DISPLAY mirror is what
this procedure records on hardware:

1. Attach (either form), call `requestPermission(CapturePermissionScope.Screen)`
   and let the REAL user (not the automation) drive the dialog: switch the
   mode spinner from "A single app" to "Entire screen", confirm. Expect the
   documented sequence — the Kadre "Screen capture" notification appears (the
   P8-0 service responsibility; the host declared nothing), `requestPermission`
   answers the stateful success with BOTH scopes
   `PermissionState.Granted`, and `open(HostChoice)` streams the WHOLE
   display: a second app brought to the foreground must appear in the frames,
   not only the capturing app.
2. The single-use token, re-read in this mode: after `requestStop`, a second
   `open(HostChoice)` without a fresh consent must answer
   `PermissionDenied(CaptureScreen)` — never a new prompt (the launcher only
   ever starts from `requestPermission`), and a fresh consent arms a new live
   session.
3. The picker cancel — the gate's refusal shape by another door: start
   `requestPermission` and press BACK on the system dialog. Expect the SAME
   stateful refusal as the "Don't allow" button (`Denied(canRequestAgain =
   true)`, never a failure), and a following `open` answering the refusal
   without a prompt.
4. Record: device, Android version, the consent mode actually offered, the
   projected-region behavior (whole display vs single app), the exact
   states/outcomes, and the notification presence/withdrawal at the terminal.

### 2. The real mid-stream revocation — the cast tile

1. Consent (any mode), open `HostChoice`, reach `Streaming` with frames flowing.
2. Stop the projection FROM THE SYSTEM — the cast/media-projection tile's
   "Stop" in Quick Settings (or the "Stop recording" chip the system posts).
   Expect the exact terminal the contract names: the session terminates
   `Stopped(PermissionRevoked)` (`collectFrames` translates it to
   `Failure(Denied(CaptureScreen))` without changing the outcome), the
   Kadre notification is withdrawn and the foreground service is gone — the
   same facts the sentinel `android-capture-fgs-stopped-after-terminal` pins
   for the requested stop, now caused by the system.
3. After the revocation, a new `open` without fresh consent answers
   `PermissionDenied(CaptureScreen)`; a fresh consent works — the refusal is
   honest, never sticky.
4. Record: the exact termination, the service/notification teardown timings,
   and the post-revocation refusals.

### 3. Real rotation mid-stream

1. Consent, open `HostChoice` (whole-display mode per procedure 1 — the
   interesting frames are the display's, not the app's fixed buffer), reach
   `Streaming`.
2. Enable auto-rotate (`adb settings put system accelerometer_rotation 1` or
   the Quick Settings tile) and rotate the device. Expect the reconfigure path
   the host record `resizeRepublishesReconfigureBeforeFrame` pins: a NEW
   `Streaming` configuration (revision+1) published BEFORE the first frame at
   the new dimensions — never a frame at the old size after the new
   configuration, never an unannounced size change.
3. Rotate during a bounded collection on the `Surface` target too: the
   SurfaceView's own buffer does not rotate (the app owns its content) — the
   honest expectation is NO reconfiguration there unless the view itself is
   resized by the host.
4. Record: the exact configuration revisions/sizes in order, and any frame that
   arrives outside the announced configuration — that would be a **finding,
   not a feature**.

### 4. FLAG_SECURE inside the captured region

The SDK redacts protected content — a documented limit (`capabilities/android.md`
§2, the `surface` row), never a choice; this procedure records the limit on
hardware:

1. Consent in whole-display mode, open `HostChoice`, reach `Streaming`.
2. Bring to the foreground an app that sets `FLAG_SECURE` (a bank app, or a
   helper activity built with `window.setFlags(FLAG_SECURE, FLAG_SECURE)`).
   In the host app's OWN display the same content is already blanked — the
   frames must show EXACTLY what the screen shows: the secured window's region
   redacted (blank) in BOTH places, never the clear content in the frames
   while the screen hides it.
3. Repeat against the `Surface` target of a view in the host app: the
   PixelCopy path copies the app's own buffer — the host app's own
   `FLAG_SECURE` window does not exist (it renders its own content), and a
   secured SECOND app never enters the copy (the copy is the attached
   SurfaceView's buffer alone). Record what the frames show.
4. Record: the app, its protection flag, the frames' content in both targets,
   and the observation that Kadre neither adds nor removes redaction — the
   SDK's own decision is delivered as reported.

### 5. Multi-window shapes

1. Consent in whole-display mode, reach `Streaming`, then enter split-screen
   (the host app and a second app). Expect the frames to keep reflecting the
   WHOLE display (the projection is a display mirror, not a window crop) and
   the reconfigure discipline of procedure 3 if the host app's region resizes.
2. With the host app in a multi-window pane, open a second session attempt:
   the capture manager answers per the contract (`Busy`/budget through the
   runtime's `maxConcurrentCaptureSessions`) — never a second silent
   projection, never a killed first stream; the foreground service is shared
   by the process-wide refcount (proven host-side by
   `AndroidCaptureForegroundTest.ownershipStopsTheServiceAtTheLastReleaseOnly`),
   and closing the first session must leave the second streaming with the
   notification still posted.
3. Close both sessions. Expect the service stopped at the LAST release and the
   notification withdrawn — nothing left running.
4. Record: the multi-window layout, the exact session/budget answers, the
   service state across the shared lifetime, and the final teardown.

## Limits and diagnostic record

Record failures with the device manufacturer and model, the Android version,
the consent mode offered, the exact reproduction steps, the readbacks above,
and all logcat errors from the host app (the capture service logs its start and
stop). Include for each procedure the full state timeline (permission states,
`Streaming` configurations with revisions, terminations with outcomes) and the
notification/service observations. Do not infer support from one manual run,
do not edit generated evidence, do not add this charter or its observations to
the validator, and do not extend the declared engine of the register
(`emulator-api-35-arm64-v8a`, read from the attached device — never hardcoded)
from one manual reading. Report the observation alongside the deterministic
gate result (`scripts/test-kadre-android-contracts.sh`) so that it can be
reproduced and turned into an automated test when the platform permits it —
the long-term home of these guarantees is the declared device pool of
`TEST-STRATEGY.md`, not a one-off pass.

## References

- [Capability register of this adapter](../../../../capabilities/android.md) — §2 (the phase 8 rows: capture HostChoice/Surface, the structural Source refusal, the service responsibility), §4 (the consent-mode hardware deferred to the device pool)
- [Android emulator contract driver](../README.md) — `AND-009`, the gate, the phase 8 limits ("A single app" self-record caveat, the `createVirtualDisplay` NPE fallback, the FGS `Temporary` retry)
- [Android Phase 7 displays/devices charter](phase-7-displays-devices.md) — the mirrored precedent (a manual complement the automation cannot stage)
- [Web Phase 7 capture charter](../../web/manual/phase-7-capture.md) — the web mirror of the same capture frontiers
- [Test strategy](../../../../TEST-STRATEGY.md) — the nightly device pool and the physical-device validation rule (a promised capability not exercable on the simulator/emulator is validated on a physical device or the promise is explicitly withdrawn)
- [Kadre design](../../../../DESIGN.md) — §15.1 (the Android attach forms), §11 (the capture model)
- [Android implementation roadmap](../../../../ANDROID-IMPLEMENTATION-ROADMAP.md) — Phase 8 (gate: refus, annulation du picker, révocation système, source perdue, rotation et fermeture pendant une frame produisent les states/outcomes exacts ; « les scénarios avec consentement réel complètent les tests déterministes »)
