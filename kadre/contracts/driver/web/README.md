# Web browser contract driver

This non-published project runs the public Web host-attachment proof in real
Chromium for both Kotlin/JS IR and Kotlin/Wasm-JS. It is a browser driver, not
an implementation API and not a renderer.

## Automated browser smoke

Run both targets from the repository root:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks
```

Fifteen target-specific Playwright suites run in Chromium, across three Playwright
projects of one invocation — the regular browser; the insecure-context project, a
browser whose host resolution maps the fixture's insecure name onto the same local
server so the page is served over plain http on a non-localhost host (see
[web-gamepad-effects.spec.mjs](playwright/web-gamepad-effects.spec.mjs) and
[web-capture.spec.mjs](playwright/web-capture.spec.mjs) below); and the capture
project, a browser launched with Chromium's own capture-test consent arguments and
the only one the `BCK-011` capture-session scenarios run in (see
[web-capture.spec.mjs](playwright/web-capture.spec.mjs) below) — and emit
target-specific JUnit results:

- [web-phase0.spec.mjs](playwright/web-phase0.spec.mjs) proves the baseline the
  other suites assume: an existing host attaches through the public API, the
  page creates no DOM of its own, and the session stops on request;
- [web-lifecycle.spec.mjs](playwright/web-lifecycle.spec.mjs) proves the
  attachment lifecycle: ownership, detach/reinsert, cross-document transfer,
  Shadow DOM observation, `Manual` reconnection, focus and visibility reduction,
  `pagehide`, and the absence of Kadre-created DOM or a primary window;
- [web-surface.spec.mjs](playwright/web-surface.spec.mjs) proves the host
  surface: the size and scale readback published from the element's own layout
  box and the browsing context's device pixel ratio, the per-animation-frame
  redraw coalescing with its detached rejection, the element escape hatch and its
  lease boundary, and the no-renderer sentinel;
- [web-input.spec.mjs](playwright/web-input.spec.mjs) proves the ordinary input
  path: the state-before-event order of a real key press, the modifiers the
  browser reported, primary pointer input, the single pointer identity the
  runtime keeps, a cancelled contact, the pixel and line wheel payloads, the
  single focus reset, the frozen snapshot of a closed surface, the page's own
  default behaviour under both members of `InputDefaultBehavior`, the pointer
  capture taken for an owned pointer, and a real touch tap delivered through
  that same lane (the touch contract itself is `BCK-004`'s);
- [web-interaction.spec.mjs](playwright/web-interaction.spec.mjs) drives the
  interaction seam with a real click and asserts only the closed set of honest
  outcomes — `committed`, or the one `refused` code the DOM exposes — recording
  the outcome this Chromium observed in the run log. It is deliberately **not**
  contract evidence: no evidence id names it, because the registry cannot pin
  which of the two honest answers a headless browser gives (see the phase 4
  limits below);
- [web-window-provider.spec.mjs](playwright/web-window-provider.spec.mjs)
  proves the seven provider scenarios of `BCK-001`: the child session opened in
  the popup the fixture host prepared before any Kadre call, and each rung of
  the validation ladder asserted through the encoded outcome. Recorded
  assumption: the scenarios depend on the runner's Chromium accepting the
  fixture's script-initiated `window.open`, which the default launch switches
  of Playwright's Chromium grant (`--disable-popup-blocking` is one of
  Playwright's own default arguments, not a switch this repository sets). If a
  future runner dropped that default, the popup would be blocked and the
  fixture would fail at its own `checkNotNull` — an honest failure, not a
  silent skip;
- [web-host-facade.spec.mjs](playwright/web-host-facade.spec.mjs) proves the
  five facade scenarios of `INT-003`, including `web-host-provider` through the
  delivered `windowProvider` option;
- [web-touch.spec.mjs](playwright/web-touch.spec.mjs) proves the eight touch
  scenarios of `BCK-004` in a browsing context that declares touch: a real
  contact driven through CDP begins, moves, ends and is cancelled as the model's
  own touch phases, two contacts keep two identities, the touch `pointerdown`
  dispatches the `TouchStarted` interaction before the stimulus, the capability
  is declared by the structural observation alone, a focus loss clears the
  contacts, and no pointer state is ever aliased by a contact;
- [web-drop.spec.mjs](playwright/web-drop.spec.mjs) proves the ten drop
  scenarios of `BCK-006` against a real `DragEvent` of a `DataTransfer` the
  fixture's host prepared before any Kadre call: one active offer, the
  synchronous accept, the rejected offer without a handler, motion and exit and
  performed drop on the accepted offer, the single claim winner, the bounded
  copied read, the teardown that closes the transfers, and no `DataTransfer`
  handle that ever crosses an interface;
- [web-text-input.spec.mjs](playwright/web-text-input.spec.mjs) proves the nine
  text-input scenarios of `BCK-005` on the `<input>` (and, for the multiline
  branch, `<textarea>`) the fixture prepared: one session per surface, the
  revision-stamped observations, a real composition driven by CDP
  `Input.imeSetComposition`, the synthetic cancellation branches Chromium
  cannot be driven into, the focus suspension, the stale-revision refusal, the
  write-back sync, the submission action, and the teardown that closes the
  session;
- [web-typescript.spec.mjs](playwright/web-typescript.spec.mjs) proves the
  published `@kadre/host` facade, driven by the same TypeScript consumer that
  `kadre/consumers/typescript` type-checks;
- [web-display.spec.mjs](playwright/web-display.spec.mjs) proves the four display
  scenarios of `BCK-007`: the `HostViewport` inventory the manager publishes when
  the session configuration installs — enumerated, one display, the primary among
  them, never `Unavailable`, never a second display — the physical bounds that
  follow a real browser-delivered resize at the browsing context's own device
  pixel ratio, the republished snapshot when the ratio itself changes at the same
  CSS size, and the teardown that leaves no further event, no republished
  snapshot, no animation-frame registration and no created node behind;
- [web-devices.spec.mjs](playwright/web-devices.spec.mjs) proves the six
  device-inventory scenarios of `BCK-008` against the synthetic gamepad source the
  fixture installs (Chromium cannot inject a real pad, the D10 precedent): the
  honest empty inventory before any pad exists, the connected pad added with its
  own standard-mapping descriptor, the per-animation-frame state poll, the neutral
  snapshot a disconnected pad leaves, the suspended session whose routing stays
  neutral, and the session close that publishes nothing further;
- [web-gamepad-effects.spec.mjs](playwright/web-gamepad-effects.spec.mjs) proves
  the five gamepad-effect scenarios of `BCK-009`: the dual-rumble effect delivered
  onto the pad's `vibrationActuator`, the stop that resets it, the unsupported
  effect kind refused before any actuator call, the insecure context whose effects
  capability stays unavailable, and the raw-input admission the web platform never
  offers. The insecure-context scenario runs only in the insecure-context
  Playwright project, so this suite appears twice in the JUnit report — four tests in the
  regular project and the insecure-context scenario alone in the insecure one;
  the testcase identity `classname#name` keeps every mapped identity unique;
- [web-capture.spec.mjs](playwright/web-capture.spec.mjs) proves the four
  control-plane scenarios of `BCK-010` — the frozen attach snapshot whose sources
  name the host picker and nothing else, the settled `display-capture` readback
  that touches no prompting API, the open refused before any picker, and the
  insecure context whose capture capabilities stay unsupported — and the five
  session scenarios of `BCK-011`: the host-choice session whose consent is a real
  click and whose delivered frames carry the published configuration's size and
  format, the configuration journal entry that precedes the first frame, the
  bounded frame collection, the exactly-once stop, and the canvas surface session
  that streams the region-carrying open without any consent call. The
  insecure-context scenario runs only in the insecure project; the five session
  scenarios run only in the capture project, whose launch arguments
  (`--auto-select-desktop-capture-source=screen`, `--use-fake-ui-for-media-stream`,
  `--use-fake-device-for-media-stream`) let the browser answer its own
  `getDisplayMedia` consent, the substitution the probe recorded in the spec
  header; the other three control-plane scenarios run in the regular project.

Playwright diagnostics are removed after a successful smoke; they are preserved
on a failure or interruption. Every identity of
[contracts/evidence.tsv](contracts/evidence.tsv) names what it maps: the four
surface scenarios, the two lease scenarios, the twelve input scenarios, the
seven provider scenarios of `BCK-001`, the eight touch scenarios of `BCK-004`,
the nine text-input scenarios of `BCK-005`, the ten drop scenarios of `BCK-006`,
the four display scenarios of `BCK-007`, the six device-inventory scenarios of
`BCK-008`, the five gamepad-effect scenarios of `BCK-009`, the four control-plane
and five session scenarios of `BCK-010` and `BCK-011` and the five facade
scenarios of
`INT-003` are titled with the evidence id they carry, so a JUnit
`testcase/@name` maps to them without interpretation; the phase 1 lifecycle
suites keep their descriptive titles, pre-dating that rule, and their rows in
the mapping carry it; and each sentinel is
mapped to the test that would fail if the mutation it guards were introduced —
which is why a sentinel id need not appear as a test title. The structural half of the interop sentinels is not JUnit
evidence at all: `platform:web`'s packaging check verifies that the two shim
bodies are shared and reconciles the shipped declarations against the curated
contract at build time.

## Contract evidence

The smoke is also the producer of the canonical browser evidence of
`kadre/WEB-IMPLEMENTATION-ROADMAP.md` section 3.8. Right after the JUnit report
validates, [contract-evidence.mjs](playwright/contract-evidence.mjs) writes one
JSON per active browser contract of the target:

```text
kadre/contracts/driver/web/build/contract-evidence/<target>/
  contract-evidence/browser/chromium/<contractId>.json
  test-results/browser/chromium/TEST-web-phase0.xml
```

The documents are built from `kadre/contracts/registry/contracts.tsv` and
[contracts/evidence.tsv](contracts/evidence.tsv), never from the test titles: a
declared scenario with no mapping row, a mapped testcase missing from the report
or a mapped testcase that did not pass fails the smoke instead of producing a
`Passed` claim. `durationMillis` is derived exactly as the validator's
`JUnitEvidence` derives it — the sum of the testsuite times scaled to whole
milliseconds with `HALF_UP` — and `tests` copies the JUnit totals. The execution
descriptor names the engine, its version read from a Playwright launch of the
same pinned revision, and the entry module the page loads, with its SHA-256. The
Wasm-JS entry module is the loader that instantiates the target's content-hashed
`.wasm`, so the digest pins that module too.

Provenance caveat: neither field pins the sources. The Kotlin/JS and Kotlin/Wasm
production bundles are not byte-reproducible, so `bundleSha256` is a per-build
digest of the artifact that was served for that run and must not be compared
across builds, and `commit` is the repository HEAD at production time whatever the
working tree held — the digest answers "which bytes ran", not "which revision
produced them".

The two Gradle tasks `:kadre:contracts:validator:validateJsBrowserContractEvidence`
and `...:validateWasmJsBrowserContractEvidence` read those documents against the
same registry, the same mapping and the same JUnit report; they are part of
`:kadre:contracts:validator:check`.

## Phase 7 capture limits

These are the real boundaries of the delivered phase, not defects. The normative
statement of the phase is the phase-7 section of
[kadre/WEB-IMPLEMENTATION-ROADMAP.md](../../../WEB-IMPLEMENTATION-ROADMAP.md)
and the adapter register `kadre/capabilities/web.md` §3.15-3.16, which each
statement below cites. The two active contracts of this phase are `BCK-010`
(the capture control plane) and `BCK-011` (capture sessions and frames):

- **no source enumeration, ever.** No browser exposes a pre-consent inventory
  of capturable surfaces, and none is invented: the published sources are
  always `CaptureSources.HostPickerOnly` (`WebCapturePort.kt:459-463`), the
  `sourceEnumeration` capability says so unconditionally in every state — an
  insecure context included — and `refreshSources()` is an honest no-op that
  returns the current snapshot object without reaching the consent machinery
  (`WebCapturePort.kt:161-166`). The Window Management API the display
  inventory already refuses (phase-6 limits above) is not called here either;
  a single pick outside the explicit request path is a scenario failure
  (`web-capture-readback-no-prompt`; sentinels `web-capture-no-implicit-prompt`
  and `web-capture-no-picker-at-readback`);
- **`CaptureTarget.Source` is structurally `Unsupported(CaptureOpen)`.** The
  port's reserve refuses a source target before anything browser-facing — zero
  seam interaction, nothing reserved, nothing asked
  (`WebCapturePort.kt:174-176`) — and the row is permanent: no source ever
  exists to target, because no inventory ever exists. A `CaptureSourceId` is
  runtime-opaque and unconstructible from this driver (the constructor is
  internal to the foundation module), and the runtime's own admission answers
  `InvalidRequest("request.target")` for an id its inventory does not name.
  The scenario that pins the refusal-before-any-picker gate in the browser
  stages the one refused open this driver can build — a region-carrying
  request (`web-capture-source-refused-before-picker`) — and the
  `Source` row itself is pinned by the platform's unit tests on both targets
  (`WebCapturePortTest.reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction`),
  with the register row carrying the `N(CaptureOpen)` cell;
- **permissions: one readback, one mirror, one explicit consent path.** The
  permission state comes from a single `permissions.query({ name:
  "display-capture" })` — a query that never prompts; the window state
  **mirrors** the screen state, because one browser consent governs whatever
  its picker offered and no distinct window guarantee exists
  (`WebCapturePort.kt:459-463`; sentinel `web-capture-window-mirrors-screen`).
  The browser's query is promise-based, so the initial snapshot carries the
  transient `Unavailable(Unsupported(CapturePermission))` on both scopes until
  the settled answer republishes the snapshot exactly once — the runtime
  dedupes identical snapshots, and an observer installed after settlement sees
  the current answer (`WebCapturePort.kt:108-111`, `:334-345`). An insecure
  context publishes `Unavailable` permission cells alongside the
  `secure-context` capability causes, still mirrored. `requestPermission(scope)`
  is the **only** explicit consent path, and both scopes run the same flow:
  one `getDisplayMedia` whose picked stream is stopped immediately — the
  browser conflates consent with picking, so the verdict is kept and the
  stream never is (the discard flow, `WebCapturePort.kt:129-159`);
- **the pump is `MediaStreamTrackProcessor`-only, and it creates no DOM.** The
  frame reader is built from the granted track's processor and its `readable`
  is read one read at a time; no video element, no processing canvas, no node
  of any kind exists on this path. Browsers that lack the primitive, or ship
  it differently, are the material of the nightly runs and
  [the manual charter](manual/phase-7-capture.md) — the capability probe
  reports the absence honestly and the register declares one engine. Two
  probed Chromium facts travel with the reader: the shipped `copyTo`
  PlaneLayout entries are `{offset, stride}` where the spec names
  `{destinationOffset, copyBytes}`, so the readers try the spec words first
  and take the shipped ones otherwise (tolerant readers, probed on the pinned
  Chromium 148, `JsWebCaptureDom.kt:304-335`); and the visible size of a frame
  is spelled `displayWidth`/`displayHeight` — there is no
  `visibleWidth`/`visibleHeight` on a `VideoFrame`, and the browser-level
  contract caught a reader of those absent words (`JsWebCaptureDom.kt:341-344`);
- **the frame bound is read before any copy, and it is terminal.** The
  browser's own `allocationSize` for the same options is read before a buffer
  exists; a frame past `maxFrameBytes` is closed without ever being copied —
  the first frame fails the start, a running frame terminates the stream —
  with `ResourceLimitExceeded(CaptureBuffer, maxFrameBytes)`
  (`WebCaptureStream.kt:170-185`, `:274-283`). No frame is re-emitted after
  the stop: a read already in flight when the stop lands is released, never
  delivered, and the counters and journal freeze at the terminal fact
  (`web-capture-no-frame-after-stop`). No stream, track, reader or frame
  object survives session end — the stub reads the browser's own `readyState`
  to prove it (`web-capture-stop-exactly-once`; sentinels
  `web-capture-track-stop-immediate`, `web-capture-no-stream-leak`). The
  browser's error names surface verbatim through the `capture-stream` platform
  failures (`WebCapturePipeException`, the normalization commit `db1f9d2d`);
- **cadence is `Unknown`, and the picker words are hints.** The browser offers
  no honest cadence primitive, so the configuration carries
  `CaptureCadence.Unknown` and no `refreshRateHz`-like promise is ever made;
  `minimumFrameInterval` travels only as the picker's `frameRate` cap hint and
  the cursor mode only as the `cursor` hint — words the browser may ignore,
  each omitted hint literally absent from the constraints dictionary
  (`WebCaptureMapping.kt:150-161`, `JsWebCaptureDom.kt:453-460`);
- **revocation is a track end, nothing more distinct.** Browsers signal
  "Stop sharing" only as the granted track's `ended` event, so the closed
  outcome of a revocation is `CaptureOutcome.SourceCompleted`; the model's
  `PermissionRevoked` stop reason has no distinct browser trigger, and that is
  a documented limit, not a synonym invented for it
  (`WebCaptureDom.kt:45-51`, `WebCaptureStream.kt:408-410`);
- **the camera is out of v1.** There is no `getUserMedia` camera path: a
  camera is a distinct surface semantics the same browser flow does not
  provide, and the decision is recorded rather than left implicit. The
  function stays canaried — the stub records any call under any name, and no
  scenario touches it; the platform sources contain zero `getUserMedia`
  occurrences;
- **region belongs to the surface alone.** A region-carrying `HostChoice`
  open is refused at admission — the AppKit-form refusal
  `Unsupported(CaptureOpen)` — before any picker, because the browser picks
  its own bounds; the surface capability is the one web target whose
  `region = Available` is real: the crop `new VideoFrame(frame, { visibleRect
  })` is staged between the reader and the pump, so the shape read, the bound
  check and the copy all bind the cropped frame, and the published
  configuration names the cropped size and the region itself
  (`WebCaptureSurface.kt:54-74`, `WebCaptureStream.kt:520-522`);
- **session-level diagnostics carry the runtime-synthesized `FrameDropped`
  only — `BackendFallback` has no SPI channel.** A native format word outside
  the promised three is converted through `copyTo`'s format option, and the
  conversion is recorded as a `CaptureDiagnostic.BackendFallback` **before**
  the frame it concerns — on the web pump's own diagnostics surface
  (`WebCapturePump.diagnostics`, the repo's first `BackendFallback` producer),
  not in the session's flow, because the stream SPI carries no
  backend-to-session diagnostics channel. The delivered shape is recorded as
  such: the conversion is bounded, announced in the effective configuration,
  and nothing claims it was delivered in the requested format
  (`WebCaptureStreamTest.conversionRequestRecordsBackendFallbackBeforeTheFrame`);
- **the session evidence's posture is the real consent path with the
  browser's own test source** (recorded limit). The probe that decided it, on
  this exact pinned Playwright and its Chromium 148 headless build, is stated
  in the spec header: `context.grantPermissions(['display-capture'])` is gone
  from Playwright 1.60's protocol mapping, `--auto-select-desktop-capture-source`
  alone is refused `NotSupportedError`, and a real compositor capture is
  refused headless on this platform. Under the sanctioned flag trio the
  browser answers its own `getDisplayMedia` — the fake UI replacing the
  interactive picker and Chromium's synthetic screen source standing in for
  the compositor — while everything else is the browser's real machinery:
  the consent call, the granted track, the processor pipe, the delivered
  `VideoFrame`s and the track stop. The real picker UX and the real pixels
  are the material of
  [phase-7-capture.md](manual/phase-7-capture.md), the phase-6 gamepad
  precedent restated for the consent flow.

### Published phase 7 availability

The canonical JSON of `BCK-010` and `BCK-011` carries the same empty
`capabilities.initial` / `capabilities.transitions` arrays as every other
browser contract, so this table is the availability record of the phase — it
names what an attached web session really publishes, and the scenario or test
next to each row is what asserts it:

| API | Published value | Asserted by |
| --- | --- | --- |
| `CaptureManager.state.sources` | `CaptureSources.HostPickerOnly` in every state — insecure context included; `refreshSources` re-answers the current snapshot at the current revision without touching the browser | `web-capture-initial-honest`; sentinel `web-capture-no-fabricated-sources` |
| `CaptureCapabilities.sourceEnumeration` | `Supported(Unit, Available)` in every state | `web-capture-initial-honest` |
| `CaptureCapabilities.hostPicker` | `Available` on a secure context with `getDisplayMedia`; `Unavailable(PlatformFailure(Web, "capture-capability", …))` with the `secure-context` then `no-get-display-media` causes otherwise — never `Unsupported` | `web-capture-insecure-unsupported`; `WebCapturePortTest.insecureContextUnsupportedEverywhereWithSecureContextCauseOnThePicker`, `.missingGetDisplayMediaUnsupportedWithNoGetDisplayMediaCauseOnThePicker` |
| `CaptureCapabilities.screen` / `.window` | `Supported(constraints, Available)` from the frozen probe — formats `{Rgba8, I420, Nv12}`, cursor `{Hidden, Embedded, EmbeddedWhenAvailable}`, region `Unsupported` — the window cell mirroring the screen cell word for word; `Unsupported(CaptureOpen)` on a missing precondition | `web-capture-initial-honest`, `web-capture-insecure-unsupported`; sentinel `web-capture-window-mirrors-screen` |
| `CaptureCapabilities.surface` | `Supported({formats = Rgba8, cursor = Hidden, region = Available}, Available)` on a canvas attach element under the same preconditions; `Unsupported(CaptureOpen)` otherwise | `web-capture-initial-honest`, `web-capture-surface-canvas-stream` |
| `CapturePermissionState` | both scopes mirrored: the settled `display-capture` readback word (`granted`/`denied:canRequestAgain=true`/`notDetermined`) or the transient `Unavailable(Unsupported(CapturePermission))` until settlement — exactly one republish | `web-capture-readback-no-prompt` (the mirrored settled cells, one query, revision 1); sentinel `web-capture-window-mirrors-screen` |
| the implicit-prompt record | empty across attach, readback, refresh and refusal: the canaries' `getDisplayMedia`, `getUserMedia`, `permissions.request`, `getScreenDetails`, `requestMIDIAccess` and `Notification.requestPermission` are never touched; the readback spy counts exactly one `permissions.query` | `web-capture-readback-no-prompt`, `web-capture-source-refused-before-picker`; sentinel `web-capture-no-implicit-prompt` |
| `CaptureManager.open` on `Source` / region | `Unsupported(CaptureOpen)` — the source target refused at the port with zero seam interaction and never constructible from the driver; the region-carrying open refused before any picker | `web-capture-source-refused-before-picker`; `WebCapturePortTest.reserveOfAnInventorySourceIsRefusedWithZeroSeamInteraction`, `WebCaptureStreamTest.hostChoiceRegionIsRefusedBeforeAnyPicker` |
| the `HostChoice` session | one explicit consent (`getDisplayMedia` exactly once, from a real click), `Streaming` at configuration revision 0, frames agreeing with the published configuration's size and format, the journal entry preceding the first frame | `web-capture-hostchoice-stream`, `web-capture-configuration-before-frame`; sentinel `web-capture-config-precedes-frame` |
| the frame bound | a `maxFrameBytes` below the first frame's `allocationSize` fails the start before any copy and terminates the session `Failed(ResourceLimitExceeded(CaptureBuffer, bound))`, zero frames delivered, the pick released | `web-capture-frames-bounded` |
| the stop | exactly once: `Stopped(Requested)` published once, the track stopped once and read `ended` by the time the outcome lands, a second stop inert, no consent machinery running again | `web-capture-stop-exactly-once`; sentinels `web-capture-track-stop-immediate`, `web-capture-no-stream-leak`, `web-capture-no-frame-after-stop` |
| the `Surface` session | open without any consent call, the region-carrying request streaming the cropped `32x32:Rgba8` configuration, the canvas track released like any other | `web-capture-surface-canvas-stream` |

## Phase 6 displays, devices and gamepad limits

These are the real boundaries of the delivered phase, not defects. The normative
statement of the phase is the phase-6 section of
[kadre/WEB-IMPLEMENTATION-ROADMAP.md](../../../WEB-IMPLEMENTATION-ROADMAP.md)
and the adapter register `kadre/capabilities/web.md` §3.12-3.14, which each
statement below cites. The three active contracts of this phase are `BCK-007`
(display inventory), `BCK-008` (device/gamepad inventory and routing) and
`BCK-009` (gamepad effects and preconditions):

- **no multi-display enumeration, ever.** No browser enumerates the displays
  behind its window, and the one that promises to — the Window Management API
  (`navigator.getScreenDetails`) — is deliberately **never called**: it is a
  prompt-forbidding permission gate (no enumeration or readback may trigger a
  permission prompt), it lives only at the top level of the browsing context,
  and it is inconsistent across engines. The inventory of an attached session
  is therefore exactly `Enumerated(primary = viewport, displays = listOf(viewport))`
  with `DisplayType.HostViewport` — the gate's mandated fallback, unconditional,
  pushed at the observer's install so a session that attached and did nothing
  already states it (`WebDisplayPort.kt:100-128`). Never an empty inventory,
  never a generic `Unavailable`, never a second display; `web-display-exact-fallback`
  and `web-display-single-display` pin the shape. The synthetic source of the
  specs poisons `navigator.getScreenDetails` to record any call
  (`gamepad-stub.mjs:122-127`), and the no-implicit-prompt sentinel asserts the
  record stays empty;
- **`refreshRateHz` is never published** (recorded limit): the browser offers
  no honest refresh-rate primitive, so the single mode carries `refreshRateHz =
  null` and the value is never guessed. `bitDepth` is `screen.colorDepth`,
  bounds and work area are the layout viewport in physical pixels —
  `round(w·dpr) × round(h·dpr)` — and `scaleFactor` is the browsing context's
  `devicePixelRatio` (`WebDisplayPort.kt:170-196`);
- **dpr-only changes ride a resolution media query re-registered on each fire.**
  A `(resolution: <dpr>dppx)` listener survives exactly one ratio change, so the
  source withdraws the fired query, re-registers for the new ratio before
  delivering the observation, and only then notifies (`JsWebDisplaySource.kt:70-83`).
  The dpr is also re-read at every measurement — the phase-2 posture — so a
  browser that never fires the query samples the ratio at the next resize;
- **gamepad discovery is poll-driven.** `navigator.getGamepads()` is the only
  state source the spec gives; the DOM `gamepadconnected`/`gamepaddisconnected`
  events carry no pad data and mean one thing — poll again now. The listeners
  are registered at the window everywhere and at the navigator only where the
  browser accepts listeners there (Chromium's navigator is **not** an event
  target — probed 2026-10-05, `navigator.addEventListener` is `undefined` there
  while `getGamepads` is a function, `JsWebGamepadDom.kt:36-59`); a target that
  refuses the registration is not an error, a missed hint costs one frame. The
  rAF poll loop runs only while at least one session port is open — the first
  open starts it and polls once immediately, the last close cancels the pending
  frame and withdraws the listeners — and a hidden page fires no frames, so it
  polls not at all (`WebGamepadHub.kt:82-96`, `:304-329`);
- **the Chromium privacy gate is not exercised by these smokes** (recorded
  limit): real pads stay invisible to `getGamepads()` until the user interacts
  with the page, and no CDP channel injects a real pad into a headless browser.
  The contract evidence therefore uses the scriptable `getGamepads` stub the
  fixture installs — the D10 synthetic-source precedent, stated in the spec
  headers (`gamepad-stub.mjs`, `web-devices.spec.mjs:5-14`) — and no scenario
  claims the gate. Real hardware (OS connect/disconnect, actual rumble, the
  privacy gate itself) is the material of
  [phase-6-displays-devices.md](manual/phase-6-displays-devices.md);
- **secure context.** `getGamepads` is secure-context-only: a real insecure page
  exposes **no pads at all**, and the published inventory stays enumerated,
  empty and complete — the honest answer, never a fabricated device. The
  per-pad effects branch that answers
  `Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect))`
  in an insecure context is defensive — a pad present in an insecure context is
  only reachable through the synthetic source, which is precisely how the
  contract reaches it. The insecure-context scenario runs only in the dedicated
  `chromium-insecure` Playwright project
  (`--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1`, the page served
  over plain http on a non-localhost name) and asserts observables only:
  `isSecureContext` false, the pad discovered and described normally, the
  effects capability unsupported, and the prompting APIs' record empty
  (`web-gamepad-effect-insecure-context`);
- **the effect preconditions are per pad, frozen at connection.** The advertised
  kinds are the actuator's own `effects` list; an actuator that declares nothing
  is probed once per connection with a zero-duration, zero-magnitude dual-rumble
  (side-effect-free per spec), and a probe that refuses advertises nothing —
  the capability is `Unsupported` (`WebGamepadEffects.kt:134-151`). `LocalizedHaptic`
  is never advertised, never launched (no browser primitive); `TriggerRumble`
  rides only when the browser declares it, its `leftTrigger`/`rightTrigger`
  magnitudes carried into the `effectParameters` dictionary — an omitted member
  is literally absent, never a zero. `maximumDuration` stays `null` (the browser
  clamps). A promise that rejects after an accepted call has no honest
  synchronous outcome: it is recorded on the failure reporter
  (`WebGamepadEffectReporting` — a page-global holder, the last wiring owns the
  page's reports), and nothing pretends the effect stopped or failed there. A
  disconnected pad refuses `Closed(Gamepad)` before any actuator call, while a
  connected-but-suspended pad's effect **launches** — the delivered semantics
  match AppKit: an effect is an app-initiated action, not input delivery;
- **raw input stays `Unsupported(RawInputAccess)` with a non-call scenario.**
  `web-gamepad-raw-input-unsupported` reads the capability cell
  (`rawInput=unsupported:rawinputaccess`), issues the refused request, and reads
  the page's own `EventTarget` registration counter around it — the delta is
  zero, so a raw channel never installs itself on request (sentinel
  `web-gamepad-raw-no-listener`);
- **generic input devices: the browser exposes no device inventory primitive**
  (recorded limit), so `DeviceInventory.Enumerated(devices = [], gamepads = …)`
  is honest with an empty devices list and nothing is fabricated for it
  (`BACKEND-CAPABILITIES.md` §5 blesses the complete-and-empty reading).

### Published phase 6 availability

The canonical JSON of `BCK-007`, `BCK-008` and `BCK-009` carries the same empty
`capabilities.initial` / `capabilities.transitions` arrays as every other
browser contract, so this table is the availability record of the phase — it
names what an attached web session really publishes, and the scenario or test
next to each row is what asserts it:

| API | Published value | Asserted by |
| --- | --- | --- |
| `DisplayManager.state` | `DisplayInventory.Enumerated(primary = viewport, displays = listOf(viewport))` with `DisplayType.HostViewport`, published at the observer's install — manager revision 1, empty event journal, an idempotent `requestAccess` re-answering at the same revision; `Unavailable(TemporarilyUnavailable(retryable))` only while the viewport is unmeasurable | `web-display-initial-hostviewport`; sentinels `web-display-exact-fallback`, `web-display-single-display` |
| the display snapshot facts | bounds/workArea `round(w·dpr) × round(h·dpr)`, `scaleFactor` = `devicePixelRatio`, `bitDepth` = `screen.colorDepth`, `refreshRateHz` = null, one mode of the same physical size | `web-display-initial-hostviewport`, `web-display-resize-propagation`, `web-display-dpr-scale-factor` |
| the display changes | exactly one republish per resize or dpr change that moves the measurement, one `DisplayEvent.Changed` per publication; a second event that measured the same viewport publishes nothing | `web-display-resize-propagation`, `web-display-dpr-scale-factor` |
| the display teardown | the session's close leaves no further event, no republished snapshot, no animation-frame registration and no created node | `web-display-teardown-quiet`; sentinels `web-display-no-dom-creation`, `web-display-no-polling` |
| `DeviceManager.state` | `DeviceInventory.Enumerated(devices = [], gamepads = …)` in every state — enumerated and honest where the poll reports nothing, at the manager revision the change published | `web-gamepad-empty-inventory-honest`; sentinels `web-gamepad-no-fabricated-devices`, `web-gamepad-no-phantom` |
| the gamepad lifecycle | one `GamepadAdded`/`GamepadRemoved` per manager revision; the descriptor frozen at connection from the pad's own mapping word (standard = the 17 buttons/4 axes in DOM order, anything else = native controls at the reported count); holes invent nothing | `web-gamepad-connect-added`, `web-gamepad-disconnect-neutral`, `web-gamepad-no-phantom`; sentinel `web-gamepad-descriptor-exact` |
| the state poll | one per-frame diff against the last observed canonical state; hostile no-op readings publish nothing; one `ButtonChanged`/`AxisChanged` per real change, stamped with the one state revision | `web-gamepad-state-poll` |
| the routing | a suspended projection publishes neutral controls and records real readings for later; the resume delivers the reading the pad reads now; the manager inventory does not move on a routing change | `web-gamepad-routing-suspended-neutral` |
| the gamepad teardown | the session's close publishes nothing further; the poll died with the last port it served | `web-gamepad-session-close-quiet`; sentinel `web-gamepad-teardown-quiet` |
| `Gamepad.playEffect` | `Supported(kinds = the actuator's own word, localizedHaptics = null, maximumDuration = null)` when the frozen connection-time probe or declaration admits; `Capability.Unsupported(Unsupported(GamepadEffect))` on an insecure context, a missing actuator, or nothing advertised | `web-gamepad-effect-dual-rumble`, `web-gamepad-effect-unsupported-kind`, `web-gamepad-effect-insecure-context` |
| the effect stop | exactly one `reset` of the actuator; a disconnected pad refuses `Closed(Gamepad)` before any actuator call; every later stop is a quiet no-op | `web-gamepad-effect-stop`; sentinel `web-gamepad-effect-once` |
| `requestRawInput` | refused `Unsupported(RawInputAccess)`; zero `EventTarget` registrations across the request | `web-gamepad-raw-input-unsupported`; sentinel `web-gamepad-raw-no-listener` |
| the implicit-prompt record | empty across every scenario: the stub's `permissions.request`, `getScreenDetails`, `requestMIDIAccess`, `share` and `Notification.requestPermission` canaries are never touched | sentinel `web-gamepad-no-implicit-prompt` |

## Phase 3 input limits

These are the real boundaries of the delivered phase, not defects. The normative
statement of the input contract is `DESIGN.md` §15.3, which the registry row
`BCK-003` cites as its source:

- a wheel whose `deltaMode` is `DOM_DELTA_PAGE` produces no observation at all
  (D9). Converting pages into pixels or lines would need a page size Kadre does
  not have, so the variant is named and dropped. `web-input-wheel-lines` drives
  one by hand and asserts that nothing was published;
- Chromium's input pipeline never reports a line-mode wheel, so the
  `ScrollDelta.Lines` payload is proven by a synthetic `WheelEvent` (D10) in the
  same test, next to the real pixel-mode wheel of `web-input-wheel-pixel`. The
  absence of a real line-mode source on Chromium is a manual-charter item;
- the runtime keeps **one pointer identity per surface**: a mouse and a pen
  observed at the same time merge onto it, with the kind of the event in hand
  (D11). `web-input-pointer-multi` proves the merge — a synthetic pen pointer
  joins the real mouse pointer in the single published entry — and the coherence
  of the two, including that no button stays stuck;
- **gestures stay `Unsupported(GestureInput)`, and touch is no longer deferred.**
  The gesture half of the old D12 boundary stands as a recorded limit, not a
  phase gap: no recognizer exists anywhere in Kadre and the browser offers no
  recognition primitive, so `InputCapabilities.gestures` stays
  `Unsupported(GestureInput)` in every state — exactly the combination
  `BACKEND-CAPABILITIES.md` §5 blesses (touch `Available`, gestures
  `Unsupported`). The touch half was delivered by phase 5 on the pointer events
  this suite already carries (`BCK-004`, `web-touch.spec.mjs`): `web-input-touch-delivered`
  keeps a smoke tab on the ordinary lane — a real tap in a touch-declaring
  browsing context publishes the contact's own touch events and never a pointer
  — and the suite keeps its own touch-declaring context for that test;
- `PointerCaptureMode.Locked` stays outside the promise of this phase: pointer
  lock needs a transient user activation and belongs to
  `InteractionAction.LockPointer` (`DESIGN.md` §9.6). `web-input-pointer-capture`
  asserts that the request is refused as `Unsupported(UpdateSurface)` rather than
  promised;
- **the activation-loss arm of a capture diverges from the browser.** Chromium
  keeps a mouse pointer capture across a focus change, so after a loss of
  activation the surface publishes `SurfaceState.pointerCapture = None` and does
  not call `releasePointerCapture`, while the browser still confines the pointer
  to the element. `web-input-pointer-capture` asserts both halves, so `None` must
  be read as "this surface claims no capture", never as "the browser holds
  none";
- the keyboard listeners live on the attached element and nowhere else, and
  making that element focusable is the host's responsibility (D7): Kadre never
  writes `tabindex`, and a host that does not make its element focusable receives
  no keyboard input. A host that detaches or closes a surface while a key is held
  leaves that key in the frozen closed snapshot — a close does not neutralise,
  exactly as the reference behaves (`RuntimeSurfaceInput.close`) — and only a
  loss of activation publishes the neutral snapshot and its single
  `StateReset(FocusLost)`.

### Published input availability

The canonical JSON of every contract carries the same empty
`capabilities.initial` / `capabilities.transitions` arrays: the schema requires
the object and both arrays, and no shape is defined for an entry, so the producer
writes none and this table is the availability record of `BCK-003` — it names
what an attached web surface really publishes, and the scenario named next to
each row is what asserts it:

| API | Published value | Asserted by |
| --- | --- | --- |
| `SurfaceInput.state` | `keyboard` and `pointer` become `Available` once the session configuration installed the observers; the state carries the pressed physical keys, the modifiers, the single pointer with its kind/buttons/position and the input revision | `web-input-key-state-before-event`, `web-input-key-modifiers`, `web-input-pointer-primary` |
| `SurfaceInput.events` | one `InputEvent` per observation, each naming the input revision the state already carries; the flow completes when the surface closes | every input scenario; the completion specifically in `web-input-terminal-closed` |
| `InputCapabilities.touch`, `InputCapabilities.gestures` | `touch` becomes `Available` with the same structural observation as `keyboard`/`pointer` (phase 5 delivers the contacts; the contract is `BCK-004`'s), and `gestures` stays `Unsupported(GestureInput)` — no recognizer exists | `web-input-touch-delivered` (delivery on the ordinary lane); the capability cell itself is asserted by every phase 5 scenario and by `WebInputSurfaceTest.keyboardAndPointerAreDeclaredOnlyByTheSurfaceOwnStructuralObservation` |
| `SurfaceCapabilities.pointerCapture` | `Supported({None, Confined}, Available)`; a `Confined` request without an owned pointer is refused `InteractionRequired(Missing)`, and `Locked` is `Unsupported(UpdateSurface)` | `web-input-pointer-capture` |
| `SurfaceCapabilities.inputDefaultBehavior` | `Supported({HostDefault, SuppressWhenPossible}, Available)`; under `HostDefault` no browser default is dropped, under `SuppressWhenPossible` only the wheel and a press of the nine document-scroll keys are | `web-input-default-behavior` |
| the terminal `SurfaceCapabilities` | unavailable: the shared all-`Unsupported` snapshot (`unsupportedSurfaceCapabilities()`, `SurfaceAdmission.kt:20`) | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` — the scenario `web-input-terminal-closed` reads `data-kadre-input-state`, a `SurfaceInputState`, plus the session and flow closure, and the fixture publishes no `SurfaceCapabilities` attribute at all |
| the input lane at the terminal transition | closed rather than reset: a key held at the close stays readable in the frozen snapshot, and no later stimulus is admitted | `web-input-terminal-closed` |

## Phase 5 touch, text input and drag-and-drop limits

These are the real boundaries of the delivered phase, not defects. The normative
statements are `DESIGN.md` §10.3 (IME), §10.4 (drag-and-drop) and the phase-5
rewrites of §15.3, and the adapter register `kadre/capabilities/web.md`
§3.9-3.11, which each statement below cites. The three active contracts of this
phase are `BCK-004` (touch delivery), `BCK-005` (text input and IME) and
`BCK-006` (drag-and-drop):

- **gestures stay `Unsupported(GestureInput)` without a recognizer** (D-T2).
  No recognizer exists in the runtime, the foundation or this adapter, and the
  browser offers no recognition primitive; the surface publishes
  `gestureKinds = emptySet()` in the very observation that declares touch
  (`WebHostSession.kt:912-925`), and the sentinel `web-touch-no-gesture-claim`
  pins the cell. `BACKEND-CAPABILITIES.md` §5 explicitly blesses pointer/touch
  `Available` with gestures `Unsupported`; a hand-rolled Pan or Pinch would be
  a hidden approximation, the same species as the dropped page-mode wheel;
- **touch rides the pointer events, and the DOM touch events are not read.**
  The contacts route through `webTouchPhase` on the existing
  `pointerdown`/`pointermove`/`pointerup`/`pointercancel` listeners
  (`WebInputMapping.kt:355-361`); no `touchstart`/`touchmove`/`touchend`
  listener exists anywhere in the two ports. A contact keeps its own
  `TouchId`, minted by the reducer per stable contact identity — the ports'
  `WebTouchContacts` table mints one identity object per `pointerId` and never
  lets a contact alias the single pointer identity (`WebInputTracking.kt:109-157`);
- **`touch-action` is the host's responsibility.** Kadre writes no style and
  no `touch-action`; a contact the browser revokes for a native scroll the host
  let through arrives as `pointercancel` and is delivered
  `TouchPhase.Cancelled` as reported, never compensated
  (`WebInputMapping.kt:345-347`). [phase-5-text-input.md](manual/phase-5-text-input.md)
  procedure 3 makes the host boundary observable;
- **the text-input v1 scope is `<input>` and `<textarea>`** (D-X3): the two
  element kinds whose value and selection *are* the document, probed with the
  browser's own `instanceof` (`JsWebTextInputEvents.kt:139-152`) so a
  non-addressable element never takes an expando write. A session opened on
  anything else — `contenteditable` included — observes nothing, and its
  write-back is refused `Closed(TextInputSession)` with the element untouched
  (`WebTextInputPort.kt:189`, `:238-241`); the open itself is never refused,
  because editability is the host's boundary (D-X2);
- **`updateCursor` has no browser effect** (recorded limit): the rect is
  accepted at the exact revision and stored in the port's shadow; the browser
  draws its own caret (`WebTextInputPort.kt:208-216`);
- **the delete `beforeinput` types produce no observation** (recorded limit):
  a delete removes a grapheme cluster and the DOM names no cluster boundary
  without the Segmentation API Kadre does not embed, so
  `deleteContentBackward`/`deleteContentForward` and every other unmapped type
  publish nothing (`WebTextInputPort.kt:257-271`) — also on a non-collapsed
  selection, where the edit would be exactly computable; only `deleteByCut`,
  whose range *is* the selection, stays mapped (`:308-314`). The conservative
  arm could be restored later on the `deleteByCut` reasoning; the next accepted
  write-back always restores the element-and-model agreement;
- **a real cancellation carries the empty string, not null.** A real
  `CompositionEvent` cannot carry null data — Web IDL stringifies it — so the
  mid-composition `Esc` of a real browser reports the removal the browser
  performed (a `Replace` the runtime refuses by its own range check, the
  application never having accepted the composition) followed by the terminal
  `CompositionChanged(null, "", null)`
  (`WebTextInputSurfaceTest.aCancellationTheBrowserPerformedReportsTheRemovalTheRuntimeRefusesAndStillEndsClean`,
  scenario `web-text-composition-cancelled`);
- **Chromium cancels a composition on a real blur.** The focus-loss suspension
  preserves the composition — the arm pinned by the Kotlin surface tests
  (`WebTextInputSurfaceTest.aFocusLossSuspendsWithTheCompositionPreservedAndARegainedFocusResumesIt`)
  and by `web-text-focus-suspends` without an active composition — but a real
  window blur makes Chromium withdraw its own composition text before the
  suspension lands, and what the element shows afterwards is the browser's
  doing. [phase-5-text-input.md](manual/phase-5-text-input.md) procedure 2
  observes it on a real IME;
- **a listener exception closes the port owner, not the runtime session**
  (recorded limit): the containment closes the owner, withdraws the listeners
  and answers `Closed(TextInputSession)` to every later operation, while the
  session the application holds stays in its current state until the
  application closes it (`WebTextInputPort.kt:395-403`). The app-closable shape
  is the delivered contract, recorded as such;
- **`keyboard.insertText` does fire `beforeinput`** — correcting the plan's
  note: the scenario `web-text-replace-event` proves the arrival as an
  `insertText` `beforeinput` and the `Replace` the port publishes
  (`playwright/web-text-input.spec.mjs:88-108`). It still produces no `keydown`
  and no composition event, so no submission action and no composition
  observation;
- **a bounded read of an unknown-size drop item answers
  `PlatformFailure(Web, "drop", "byte-read")`, not the limit failure**
  (recorded limit): the runtime can only bound an unknown-size total mid-read,
  throwing from the collector it hands the source, and the port's containment
  converts that throw into the seam's platform failure before the runtime's own
  `ResourceLimitExceeded` branch is reached (`RuntimeDropTransfer.kt:338-355`,
  `WebDropStimulus.kt:161-175`). A known-size item keeps the up-front check
  (`RuntimeDropTransfer.kt:314`). The read fails closed either way; the failure
  shape differs, and the register records the difference rather than paying a
  runtime change for it;
- **`dragover` without an offer re-snapshots and re-presents** (the D-D1
  re-present rule, now the model's own semantics): the DOM's bubbling spends
  offers behind the element's back — a child's `dragenter` fires before the
  parent's `dragleave` — and a rejected offer leaves the same state, so an
  offer-less `dragover` rebuilds the snapshot and re-presents through the same
  seam, and with a rejecting handler every offer-less `dragover` re-dispatches
  a presentation (`JsWebDomPort.kt:878-907`);
- **`preventDefault` is target activation, never default suppression** (D-D3):
  the `dragover` and `drop` handlers call `preventDefault()` only while the
  surface holds the accepted offer of the drag in hand — the one thing that
  makes the element a drop target at all — and the closed
  `SUPPRESSED_INPUT_DEFAULTS` set is not extended. These are the only three
  `preventDefault` sites of each port (`JsWebDomPort.kt:986-1006`), pinned by
  the sentinel `web-drop-no-prevent-default-without-offer`;
- **Kadre is never the source of a drag.** Only incoming drops are received;
  there is no drag-out, and no `DropEntered` stimulus exists — the offer is
  presented by the host-side seam call, the model's own shape
  (`DESIGN.md` §10.4). A real drag from the OS (a file from Finder, text from
  another tab) cannot be staged honestly headlessly and is the procedure of
  [phase-5-text-input.md](manual/phase-5-text-input.md), procedure 5.

### Published phase 5 availability

The canonical JSON of `BCK-004`, `BCK-005` and `BCK-006` carries the same empty
`capabilities.initial` / `capabilities.transitions` arrays as every other
browser contract, so this table is the availability record of the phase — it
names what an attached web surface really publishes, and the scenario or test
next to each row is what asserts it:

| API | Published value | Asserted by |
| --- | --- | --- |
| `InputCapabilities.touch` | `FeatureAvailability.Available` once the session configuration installed, from the structural observation alone (`touchInstalled = true`); `Unsupported` before installation and `Unavailable(SourceOverflow(InputSource))` on the terminal overflow arm | every `web-touch.spec.mjs` scenario (capability cell), `web-touch-capability-structural` in particular; `web-input-touch-delivered` on the ordinary lane |
| `InputCapabilities.gestures` | `Capability.Unsupported(Unsupported(GestureInput))` in every state, overflow arm included | `web-touch-capability-structural`, sentinel `web-touch-no-gesture-claim` |
| `SurfaceInput.state.touches` / `InputEvent.TouchChanged` | one `TouchState` per live contact, keyed by the reducer's own `TouchId` allocation; `pointers` and `touches` disjoint; a focus loss clears the contacts with the neutral snapshot | `web-touch-started`, `web-touch-multi-contact`, `web-touch-ended`, `web-touch-cancelled`, `web-touch-no-pointer-alias`, `web-touch-focus-loss-clears` |
| the interaction trigger of a touch | `InteractionEvent.TouchStarted` dispatched synchronously from the touch `pointerdown`, before the ordinary stimulus | `web-touch-interaction-trigger` |
| `InputCapabilities.textInput` | `Capability.Supported(Unit, Available)` structurally, at the session configuration; the pre-install input state and the terminal overflow arm publish `Capability.Unsupported(Unsupported(TextInput))`; a second open while one session is live is `AlreadyInUse(TextInputSession)` | every `web-text-input.spec.mjs` scenario (capability cell); `web-text-open-single-session` |
| the text session events | `TextInputEvent.Replace`/`SelectionChanged`/`CompositionChanged`/`Action`, each stamped with the accepted document revision; composition end is the terminal `CompositionChanged(null, "", null)`; a focus loss suspends and a regained focus resumes with the composition preserved | `web-text-replace-event`, `web-text-composition-lifecycle`, `web-text-composition-cancelled`, `web-text-focus-suspends`, `web-text-action-submit` |
| the write-back | `updateSurroundingText` writes `value` + `setSelectionRange` on the addressed element (`<input>`/`<textarea>` v1 only) and re-syncs the shadow; a stale revision is refused, a non-addressable element closes the session with `Closed(TextInputSession)` and the element untouched | `web-text-writeback-sync`, `web-text-stale-revision` |
| teardown | the surface's termination closes the session, withdraws the listeners and delivers no late observation | `web-text-teardown-closes` |
| `InputCapabilities.dragAndDrop` | `FeatureAvailability.Available` once the session configuration installed (`dragAndDropAvailable = true`); `Unsupported` before installation and `Unavailable(SourceOverflow(InputSource))` on the terminal overflow arm | every `web-drop.spec.mjs` scenario (capability cell), `web-drop-presented-single-active` in particular |
| the drop offer flow | `DropOffer` presented by the seam's `presentDrop` (one active offer, the previous one `LeftSurface`), `InteractionEvent.DropEntered` dispatched in the `dragenter` frame, `AcceptDrop` accepted or the offer rejected; `dragover` with an offer → `DropMoved`, `dragleave` → `DropExited`, `drop` → `DropPerformed` making the transfer claimable | `web-drop-presented-single-active`, `web-drop-accept-synchronous`, `web-drop-rejected-without-handler`, `web-drop-moved-after-accept`, `web-drop-exit-terminates`, `web-drop-performed-claimable` |
| the transfer | exactly one claim winner, bounded copied reads (`collectBytes`), one reader at a time; an item the browser never let the port attach answers `PlatformFailure(Web, "drop", "payload-unavailable")` | `web-drop-claim-single-winner`, `web-drop-read-bounded-copy` |
| teardown and leak sentinels | the session's terminal transition closes the offer and the transfers exactly once; no `DataTransfer`, `File` or `Blob` handle crosses a Kadre interface | `web-drop-teardown-closes`, `web-drop-close-exactly-once`, `web-drop-no-data-transfer-leak`, `web-drop-no-fabricated-mime` |


## Phase 4 interaction and window limits

These are the real boundaries of the delivered phase, not defects. The normative
statements are `DESIGN.md` §9.6 (the token model, unchanged by the lift to
`commonMain`), the `WebWindowProvider` rows of `OPERATION-CONTRACTS.md` §4, and
the adapter register `kadre/capabilities/web.md` §3.7-3.8, which each statement
below cites:

- **a browser refusal is not discriminated.** A `fullscreenerror`, a
  `pointerlockerror` and a rejected promise all arrive without a reason, so a
  refused primitive carries the one honest code
  `PlatformFailure(Web, "fullscreen"|"pointer-lock", "refused")`
  (`WebInteractionStimulus.kt:84-93`), and an emission failure — the port could
  not even ask — answers the very same failure. There is no permission, no
  document-state and no element-state code, because the DOM names none;
- **no synthetic timeout on a deferred pending.** A fullscreen or pointer-lock
  request whose terminal callback never fires holds its
  `maxPendingInteractionRequests` slot (16 under `KadrePolicies.Default`) until
  the surface terminates, which abandons every pending with
  `Closed(KadreResourceKind.Interaction)` — one `Rejected` per pending, then the
  end of the flow (`RuntimeInteractionHandler.kt:167-178`,
  `WebHostSession.kt:1562-1565`). Kadre invents no deadline the browser does not
  keep;
- **headless versus real screen.** What the headless smoke proves is what this
  Chromium really did, never what it was hoped to do: the
  `web-interaction-fullscreen` scenario clicks the host for real and asserts
  only the closed set `{committed, refused}` — the run of this phase observed
  `committed` on both targets, i.e. headless Chromium honoured the fullscreen
  primitive for a real trusted click. Which answer a given headless
  configuration gives is an observation, not a contract promise, which is why
  the scenario is smoke, not evidence, and why the real-screen procedures of
  [phase-4-interactions.md](manual/phase-4-interactions.md) exist;
- **the transient-activation ruling.** Kadre reads no `navigator.userActivation`
  anywhere: the paired `pointerdown`/`keydown` listeners are already trusted
  activation-bearing events, `hasBeenActive` is sticky and cannot distinguish
  "this frame", and second-guessing the browser would turn a legitimate browser
  refusal into a premature Kadre refusal. A real loss of activation surfaces as
  the primitive's own `refused` outcome. Recorded as a documented limit, not a
  gap (`kadre/capabilities/web.md` §3.7, item 6);
- **the interaction dispatcher is wired to the input listeners, not the input
  lane.** Interactions dispatch synchronously from `pointerdown`/`keydown`
  before the ordinary stimulus is enqueued; the interaction budget
  (`maxPendingInteractionRequests`) and the input ingress budget
  (`discreteCapacity`, `IngressOverflowAction`) are separate policies, and an
  interaction never passes through `SurfaceInput.events`;
- **`OpenWindow` stays `Unsupported` even with a provider** (`DESIGN.md:1933`):
  the opening path is `WindowManager.requestWindow` plus the provider, never the
  interaction action, whose refusal at admission reaches no browser API;
- **window budget accounting holds the slot until the requester's `close()`.**
  The reference evicts a request from its `maxPendingWindowRequests` budget at
  handoff (`RuntimeWindowManager.kt:522-526`); the web manager cannot, because a
  synchronous provider publishes the terminal outcome before the caller ever
  sees the request — an evict at handoff would empty the budget on every request
  and `Limit(WindowRequest)` would never limit. The admitted request therefore
  holds its slot until `WindowRequest.close()` releases it or the session
  terminates (`WebHostWindowManager.kt:222-242`, `:314-316`, `:249-256`). This
  is the one deliberate deviation from the reference's accounting, recorded
  here and in the register (`kadre/capabilities/web.md` §3.8, item 5);
- **the delivered validation ladder orders its rungs by the field names of
  `OPERATION-CONTRACTS.md` §1.1.** The contract's prose sentence lists
  `element.ownerDocument` before `element`; the delivered ladder evaluates
  `element` first, then `element.ownerDocument`, then `parentScope`, then
  `ParentScopeCancelled` (`WebHostWindowManager.kt:113-122`), and the order is
  pinned by `WebHostWindowManagerTest` (`:394-402`). With every rung failing,
  the only answer is `InvalidRequest("element")`. The chosen order is recorded
  here because the prose and the code enumerate differently;
- **the real-screen primitives that headless cannot stage honestly** — a real
  user-activated fullscreen with the browser's own `Esc` exit, a real pointer
  lock with the pointer hidden and confined, a real visible popup — are the
  procedures of [phase-4-interactions.md](manual/phase-4-interactions.md),
  which is informative and creates no validator evidence.

### Published interaction and window availability

The canonical JSON of `BCK-001` and `INT-003` carries the same empty
`capabilities.initial` / `capabilities.transitions` arrays as every other
browser contract, so this table is the availability record of the phase — it
names what an attached web session really publishes, and the scenario or test
named next to each row is what asserts it:

| API | Published value | Asserted by |
| --- | --- | --- |
| `SurfaceCapabilities.handlerInteractions` | `Unsupported(InstallInteractionHandler)` before installation and at the terminal snapshot; `Supported({EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer, AcceptDrop}, Available)` once the session configuration installed, at the same structural moment as `keyboard`/`pointer` — the phase 5 `AcceptDrop` is the one action with no browser primitive behind it, answered by the drop seam | `WebInteractionSurfaceTest.handlerInteractionsIsTheFiveWebActionsOnceStructurallyInstalled`, `.thePreInstallSnapshotClaimsNoInteractionAtAll`; the smoke scenario `web-interaction-fullscreen` reads the armed flag `data-kadre-interaction-armed` the fixture sets after a successful install |
| `SurfaceCapabilities.armedInteractions` | `Unsupported(ArmInteraction)` in every snapshot — no platform implements the arm path | `WebInteractionSurfaceTest.armedInteractionsRemainsUnsupported` |
| `InteractionContext.request` | the common engine's admission: `Expired`, `WrongSurface`, `Consumed`, `Unsupported(Interaction)` at admission, `Closed(Interaction)` when the registration closes during the native call (both `Now` and `Deferred` paths), `ResourceLimitExceeded(Interaction, limit)` past the deferred budget; a `LockPointer` mode other than `Locked` is `InvalidRequest("action.mode")`; the terminal outcome is `Committed` or `Rejected` — `Committed` only after the browser confirmed | `RuntimeInteractionHandlerCommonTest` (`duplicateRetainedExpiredAndUnsupportedRequestsFailWithoutCallingNativeCode`, `requestRefusesWithClosedWhenTheRegistrationClosesDuringTheNativeCall`, `deferredAdmissionRefusesWithClosedWhenTheRegistrationClosesDuringTheNativeCall`, `pendingBudgetExceededRefusesWithResourceLimit`), `WebInteractionSurfaceTest` (`aRetainedContextIsRefusedExpiredAfterTheHandlerReturns`, `lockPointerRefusesEveryModeButLockedAsInvalidActionMode`, `aDeferredTerminalRefusalIsRejectedWithTheRefusalFailureAndFreesTheBudget`, `aSynchronousExitTerminalCommitsThePending`) |
| the interaction outcomes flow | one `InteractionActionOutcome` per admitted request, terminal only — `Committed` at the browser's change event, `Rejected` at its error event, promise rejection, emission failure or abandonment; the flow completes at the surface's terminal transition | `web-interaction-fullscreen` (closed set, one outcome per click); `WebInteractionSurfaceTest.terminationAbandonsDeferredRequestsWithClosedInteraction` |
| `WindowManagerCapabilities.requestWindow` | without a provider: `Unsupported(RequestWindow)` and a request already terminal `Rejected(Unsupported(RequestWindow))` — the `web-no-implicit-window` behaviour, unchanged; with a provider: `Supported({OpenedInNewSession}, Available)`, `OpenedHere` never promised, `primary` staying `null` | `web-no-implicit-window`; `web-window-provider-new-session` (capability readback `data-kadre-window-caps`), `WebHostWindowManagerTest` |
| `WindowRequest` semantics with a provider | the outcome is published before the caller sees the request: `await()` returns it, `cancel()` answers `AlreadyTerminated`, `close()` releases the budget slot only; rejections are outcomes of the request, never failures of `requestWindow` | `web-window-provider-same-context`, `-no-context`, `-invalid-element`, `-invalid-scope`, `-owned-element`, `-callback-failure`; `WebHostWindowManagerTest` |
| the child session | opened through the ordinary attach path in the host-prepared context, launched `AdditionalHostRequested` with the originating request id, owned through the shared registry — an element a live session owns is refused `Busy(Host)`; closing the requester never closes the child, and the child's scope is never the requester's | `web-window-provider-new-session` (child identity on the offered element), `web-window-provider-owned-element`; `WebHostWindowManagerTest` |
| the facade `windowProvider` option | `KadreWebOptions.windowProvider` delivered with the three promised interfaces byte-verbatim in `kadre-host.d.ts`; an option that is present but not callable is refused `InvalidRequest("options.windowProvider")`, never silently ignored; the child's scope is a fresh Kadre `MainScope` per opened host | `web-host-provider`; `WebHostInteropTest.aPresentWindowProviderOptionWithoutCallableOpenIsRefusedInsteadOfIgnored`, `.aProvidedWindowProviderOptionReachesTheAttachUnchanged` |


## Phase 2 limits

These are the real boundaries of the delivered phase, not defects:

- a redraw buffer whose overflow action is `DropOldestAndReport` or
  `DropLatestAndReport` only ever drops the excess request: a surface has no
  diagnostic channel in this phase that could carry the report half;
- a buffered redraw capacity bounds *admissions per animation frame*, not a
  queue of undelivered events: every request in one task coalesces into the one
  pending request its frame admits, so `Latest` and `Coalesced` are
  indistinguishable at the publisher, and a `Recording` profile's `FailSession`
  action is reached only by an in-frame burst larger than the declared capacity.
  The JVM reference bounds a queue of undelivered events instead;
- the terminal `Surface.events` stream completes on Web where the JVM reference
  terminalises it with a failure, so a collector reads the session outcome to
  tell a clean close from a reported overflow;
- `SurfaceCapabilities.platformAccess` is published once, from the terminal path:
  inside the ownership-revocation window a lease is already refused while the
  capability still reads `Supported`;
- the published Wasm module is the unoptimized compiler output, because
  `wasm-opt` empties a library distribution: the `build/dist/wasmJs` copy is never
  shipped.

## The TypeScript scenario

The `web-typescript-consumer` scenario runs the source that
`kadre/consumers/typescript` type-checks, in the browser, against the target's
published `@kadre/host` package. The driver assembles the served root into
`build/dist/<target>/host/`: the published package plus the compiled
`consumer.js` (`:kadre:emitTypeScriptBrowserConsumer`). The generated page
resolves the bare specifier `@kadre/host` through an import map to the
package's published `index.mjs` shim, served straight from that directory, and
then runs the compiled consumer. Nothing is remapped or substituted: the shim
under test is the shipped one.

The bindings the shim drives come from the shared registry the Kotlin module
publishes them into, `globalThis["org.graphiks.kadre:web"]`. The page's Kotlin
application — the fixture bundle, whose `applicationFactory()` entry point
publishes the bindings of its own instance with `publishHostBindings()` and
returns the opaque factory key — is therefore the instance that owns the
session the consumer drives, which is the shape `kadre/INTEROP-EXPORTS.md`
section 6 describes. The consumer is the only thing that attaches, subscribes,
unsubscribes, stops and awaits the outcome; the spec asserts that all eight
binding names are present in the registry, and that the consumer reported
`passed`.

Standalone use, recorded decision: the shim loads no Kotlin module and holds no
bindings of its own, so the application must publish before it calls
`KadreWeb.attach` — its Kotlin module calls `publishHostBindings()`, the Kotlin
half of `INTEROP-EXPORTS.md` section 6 that also produces the opaque key the
shim carries back. Resolution happens inside `KadreWeb.attach`, never at import,
so import order and a publication that follows an asynchronous `main` both work.
The last publisher wins; a page that publishes nothing is reported with
`@kadre/host: the Kotlin module did not publish its bindings`.

`--consumer=<directory>` is required: the runner serves that directory at
`/host` and fails before Playwright starts when it is absent or does not carry
`index.mjs`. `--contracts=<registry>` and `--mapping=<file>` are required too:
they are the registry and the evidence mapping the canonical JSON documents are
built from, and the Gradle smoke task passes the repository's own pair. That task
also passes `--commit=<repository HEAD>` (or the `kadreContractCommit` override),
which is what the validator compares against — a document produced at another
commit is stale and is rejected rather than silently accepted.

## Pinned Chromium provisioning

The Gradle task `installPlaywright` runs only the locked `npm ci
--ignore-scripts`; it does **not** provision a browser executable. From a clean
checkout, provision the Chromium revision pinned by that local Playwright
installation separately:

```shell
rtk ./gradlew :kadre:contracts:driver:web:installPlaywright
(cd kadre/contracts/driver/web && npx --no-install playwright install chromium)
```

The browser smoke tasks then use that local pinned Chromium revision. A system
browser version is not an input to the automated smoke.

## Manual browser charter

Two charters supplement the automated suites with browser behaviour that
headless automation cannot claim reliably, and neither replaces the JS and Wasm
automated smokes:

- [phase-1-lifecycle.md](manual/phase-1-lifecycle.md) covers a real back-forward
  cache traversal, browser-level focus, and host owned open/closed Shadow DOM;
- [phase-2-surface.md](manual/phase-2-surface.md) covers a real browser zoom, a
  bfcache traversal with a redraw request still waiting for its frame, and a
  host-owned element moved between browsing contexts while an element lease is
  held;
- [phase-3-input.md](manual/phase-3-input.md) covers the real `deltaMode` a
  wheel reports per engine, trackpad momentum, a non-US keyboard layout, real
  pen hardware and the single-pointer merge;
- [phase-4-interactions.md](manual/phase-4-interactions.md) covers the
  interaction and window primitives of phase 4 on a real screen: a real
  user-activated fullscreen with the browser's own `Esc` exit, a real pointer
  lock with the pointer hidden and confined, the token rules observable from a
  page console, and the real visible popup the provider scenarios only reach
  headlessly;
- [phase-5-text-input.md](manual/phase-5-text-input.md) covers the real OS IME
  (activation, candidates, commit, mid-composition `Esc` cancellation), a real
  multi-layout keyboard, the host's own `touch-action`, the `contenteditable`
  element the v1 text-input contract does not address, and a real OS drag into
  the element, which headless automation cannot stage;
- [phase-6-displays-devices.md](manual/phase-6-displays-devices.md) covers the
  real hardware gamepad (OS connect/disconnect, actual rumble, Chromium's
  privacy gate that headless cannot stage), the real multi-display setup the
  adapter deliberately does not enumerate (the Window Management API documented
  as unsupported), the insecure-context matrix beyond the fake-hostname trick,
  and the `vibrationActuator` behaviour of Firefox/WebKit;
- [phase-7-capture.md](manual/phase-7-capture.md) covers the real consent flow
  the capture scenarios only reach through the browser's own test arguments:
  the real picker (dialog, source thumbnails, the user's own choice), the real
  screen/window/tab pixels including cursor behaviour, the macOS
  screen-recording permission interplay with the browser's consent, the
  "Stop sharing" bar the track-end revocation rides, the
  `MediaStreamTrackProcessor` and consent behaviour of Firefox/WebKit, and the
  camera-out-of-v1 confirmation.

They are informative, they do not create validator evidence, and they do not
change contract status.

The lifecycle contract and the delivery roadmap remain authoritative:

- [Web implementation roadmap](../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre design](../../../DESIGN.md)
- [Operation contracts](../../../OPERATION-CONTRACTS.md)
