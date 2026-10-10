# Web Phase 7 — Capture manual charter

This is a short manual complement to the deterministic Phase 7 browser tests.
Its procedures cover the consent-flow frontiers headless automation cannot
honestly produce: the real picker (the dialog, its source thumbnails, the
user's own choice among screens, windows and tabs), the real captured pixels
including the cursor's behaviour, the macOS screen-recording permission that
sits behind the browser's own consent, the "Stop sharing" bar whose click the
browser reports only as the track's end, the `MediaStreamTrackProcessor` and
consent behaviour of Firefox/WebKit — the engines the pinned Chromium smoke
cannot speak for — and the camera-out-of-v1 confirmation. The session
evidence of `BCK-011` runs in the `chromium-capture` Playwright project under
Chromium's own sanctioned capture-test arguments, the fake UI replacing the
interactive picker and the synthetic screen source standing in for the
compositor; the probe that decided that posture is recorded in the
[web-capture.spec.mjs](../../playwright/web-capture.spec.mjs) header, and this
charter is where the real picker and the real pixels are recorded instead. It
is informative; it creates no validator evidence, it does not change any
contract status, and it is not wired into the contract validator. It never
replaces an automated proof, and nothing in it may be read as the capability
register: the register of this adapter's capture features is
[capabilities/web.md](../../../../capabilities/web.md) (§2, §3.15-3.16), and it
declares the one engine the automated smoke really runs.

## Scope and prerequisites

Phase 7 covers the capture control plane (`CaptureManager`), the capture
sessions and their bounded frames. The delivered boundaries are stated in the
phase-7 section of
[the implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md), in
[the driver's Phase 7 limits](../README.md#phase-7-capture-limits) and in the
register sections cited there; the observations below exercise their edges
against a real consent, a real compositor and a real user. Kadre still creates
no DOM node and still triggers no implicit permission prompt — the automated
no-implicit-prompt sentinel is the pin, and every procedure below must keep it
true: the only browser consent machinery this adapter ever starts is the
explicit open of the application.

Every procedure uses the existing public Web driver fixture selected by
`?scenario=` (`capture` for the control plane, `capture-session` and
`capture-bounded` for the session scenarios). Build both distributions and
serve the static host of [phase-1-lifecycle.md](phase-1-lifecycle.md) before
starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
# then, from the temporary static host of the Phase 1 charter:
#   http://127.0.0.1:8080/js/index.html?scenario=capture
```

Run both target smokes before the manual pass
(`jsBrowserSmoke` and `wasmJsBrowserSmoke`, `--rerun-tasks`). Their suites
cover the deterministic contract — the control-plane scenarios in the regular
browser, the session scenarios under the sanctioned flag trio — and the
procedures below may reveal an engine-specific issue, but may not be used to
fabricate contract evidence or to claim that an automated branch has passed.

| Target | Automated prerequisite |
| --- | --- |
| Kotlin/JS IR | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

The observation surface is the same for every scenario. The fixture attaches
a host through the public API and republishes what the manager and the
session publish as attributes of that host, so an attribute a procedure reads
is a fact a consumer of this API can read:

| Attribute | Scenario | What it carries |
| --- | --- | --- |
| `data-kadre-capture-permissions` | `capture`, `capture-session` | the mirrored permission cells (`screen=…:window=…` — always identical) |
| `data-kadre-capture-capabilities-screen` / `-window` / `-surface` | `capture`, `capture-session` | the frozen capability cells of the three targets |
| `data-kadre-capture-host-picker` | `capture`, `capture-session` | the picker's `FeatureAvailability` (`available`, or the unavailable failure encoding) |
| `data-kadre-capture-sources` / `-enumeration` / `-revision` | `capture`, `capture-session` | the inventory (`hostPickerOnly`), the enumeration capability (`supported`) and the manager revision (moves from 0 to 1 when the readback settles) |
| `data-kadre-capture-open` | `capture`, `capture-session` | the answer of the last open (`success`, or the encoded failure) — in `capture` the region-refused open, in `capture-session` the button's host-choice open |
| `data-kadre-capture-refresh` | `capture` | the answer of `kadre-capture-refresh` (`success@<revision>` — the honest re-answer) |
| `data-kadre-capture-session-state` | `capture-session` | the session state (`ready`, `streaming:rev=N`, `terminated:…`) |
| `data-kadre-capture-session-events` | `capture-session` | the event journal (`streaming-started@rev=N:size=WxH:format=F;…`) |
| `data-kadre-capture-frames` / `-frame-facts` | `capture-session` | the delivered frame count and each frame's `size:format:rev` fact |
| `data-kadre-capture-outcome` | `capture-session` | the terminal outcome (`completed`, `stopped:requested`, `failed:…`) |
| `data-kadre-capture-collect` / `-open-surface` | `capture-session` | the answer of the collect and surface-open commands |
| `data-kadre-capture-paints` | `capture-session` | the fixture's own canvas paint counter |

In the procedures below, `host` is the scenario's own element,
`document.querySelector('[data-kadre-host="<scenario>"]')`, the host-choice
open is driven by a **real click** on the fixture's own button
(`[data-kadre-capture-open-button="host-choice"]` — the gesture a real
consent asks for), and a fixture command is dispatched with
`document.dispatchEvent(new Event("<command>"))` on `document` — the commands
`kadre-capture-refresh` and `kadre-capture-open-region` exist in the
`capture` scenario, `kadre-capture-collect`, `kadre-capture-stop` and
`kadre-capture-open-surface` in `capture-session`. Unlike the
automated session scenarios, a manual run needs none of the capture-test
launch arguments: the real picker is the point.

## Procedures

### 1. The real picker: the dialog, the thumbnails, the choice

Open `http://127.0.0.1:8080/js/index.html?scenario=capture-session` (replace
`js` with `wasm` for the Wasm-JS bundle) in a **non-headless** Chromium, wait
for `data-kadre-ready`, and confirm the quiet attach:

```js
const host = document.querySelector('[data-kadre-host="capture-session"]');
host.getAttribute("data-kadre-capture-open");   // null — nothing asked yet
```

Click the host-choice button. The browser's own picker appears — its dialog,
its source thumbnails, its three surfaces (screen, window, tab) exactly as
the browser renders them for any web application — and no frame exists before
you choose. What the automated suite substitutes with `--use-fake-ui-for-media-stream`
is exactly this dialog; record what this browser really showed: the picker's
shape, whether the current tab was preselected, whether a preview moved
before the choice. Choose a source and read the consented stream:

```js
host.getAttribute("data-kadre-capture-open");   // success
host.getAttribute("data-kadre-capture-session-state");   // streaming:rev=0 (after kadre-capture-collect)
```

The record of this pass is the picker itself: which surfaces the browser
offered, what the thumbnails showed, and that the stream started only after
the choice — the consent boundary the control plane never crosses on its own.
Then click the browser's **"Stop sharing" bar** (procedure 4) and record that
the revocation is the browser's own UI, not the page's.

### 2. Real pixels: screen, window, tab, and the cursor

With a session streaming from each surface kind in turn (procedure 1's picker,
choosing `Entire screen`, then a `Window`, then `This tab`), read the frames
the fixture reports and compare them with what is actually on screen:

```js
host.getAttribute("data-kadre-capture-frame-facts");   // <W>x<H>:<F>:rev=0 per frame
host.getAttribute("data-kadre-capture-session-events"); // streaming-started@rev=0:size=WxH:format=F;…
```

The published configuration's size is the browser's frame size and the
frames' facts must agree with it — that is the contract; what this pass adds
is the visual truth: a window capture follows the chosen window, a tab
capture follows the tab, a screen capture follows the whole display, and the
delivered size follows the browser's own choice (a size different from the
on-screen pixels is the browser's answer, recorded, never compensated).
**The cursor** is the one pixel the request's word cannot force: repeat the
pass with the three modes the fixture's requests can carry
(`EmbeddedWhenAvailable` is the default, `Embedded` and `Hidden` the
alternatives) and record what the browser actually embedded — the `cursor`
picker word is a hint the browser may ignore, so the record is what the
pixels show: cursor present, cursor present only while moving, or cursor
absent, per surface kind and per engine. Whatever the browser did is the
delivered truth; the request's cursor mode is not.

### 3. macOS screen-recording permission interplay

On macOS, the browser's own consent and the operating system's Screen
Recording permission are two different gates, and only the browser's is the
page's to ask. Open the picker on a machine where the browser lacks (or has
just lost) macOS Screen Recording permission and choose another application's
window or the whole screen: record what the compositor actually delivered —
typically desktop wallpaper and the browser's own windows without the
content of other applications, or a system prompt offering to grant it —
while the page-side facts stay exactly what the contract publishes:

```js
host.getAttribute("data-kadre-capture-permissions");   // the browser's readback word, mirrored — not the OS's
host.getAttribute("data-kadre-capture-open");          // success — the browser consented; the OS pixels are the OS's
```

The screen-recording grant is browser-scoped, not page-scoped: grant it once
to the browser in System Settings, restart the browser, and the same capture
shows the other applications' content. The OS permission appears nowhere in
`CapturePermissionState` — the readback the model publishes is the browser's
`display-capture` word, and that is the honest boundary: Kadre states what
the browsing context states, and the OS gate is the user's, outside any page.
Record the macOS version, the browser version, the permission state at each
step and exactly what the pixels showed.

### 4. Revocation: the "Stop sharing" bar, the tab, the end of the track

With a session streaming (procedure 1), click the browser's **Stop sharing**
bar. The browser ends the granted track — the only shape a revocation has on
the web, there is no distinct "permission revoked" signal — and the session
terminates with the closed outcome:

```js
host.getAttribute("data-kadre-capture-outcome");         // completed
host.getAttribute("data-kadre-capture-session-state");   // terminated:completed
host.getAttribute("data-kadre-capture-frames");          // frozen — nothing after the end
```

Record the moment of the click, the outcome read, and that no late frame
arrived. Then repeat the pass ending the capture the other ways a user can:
navigating the captured tab away (the source dies with it), closing the
captured window, and the picker-less `kadre-capture-stop` command — each
landing in one of the closed outcomes (`completed` for the browser's ends,
`stopped:requested` for the app's own stop), each releasing the track
(the browser's own media indicator leaves the tab), each terminal exactly
once. A session the application closes (`kadre-capture-stop`) must take the
browser's indicator down with it — the stop reaches the track, not only the
collector.

### 5. Firefox/WebKit: the processor, the consent, the pixels

The pinned smoke runs Chromium only, and the register declares one engine;
the other two differ in exactly the places the probe guards, and this
procedure records which guard fired. Load `?scenario=capture` and
`?scenario=capture-session` in a **Firefox** and a **WebKit** build and read,
per engine:

- **the pump primitive** — `MediaStreamTrackProcessor` is absent on both
  engines today (Firefox ships it only behind a nightly flag, WebKit not at
  all): the guarded probe answers `null`, the three target capabilities read
  `unsupported:captureopen`, and the picker's availability stays
  `available` on a secure context — the honest pair of a context that can
  consent but has no pump this adapter reads. A `getDisplayMedia` consent is
  still granted by the browser; nothing reserves frames it cannot pump.
  Record the engine version and, on a Firefox nightly with the flag, whether
  the pipe behaves — a second engine becomes a declared minimum only through
  its own evidence, never through a manual reading;
- **the consent** — the picker is each engine's own: Firefox's dialog names
  its surfaces its way and WebKit's (Safari) splits screen selection into
  the OS system picker. Whatever the dialog, the page-side sequence is the
  delivered contract: no capability cell moves at attach, the open's single
  `getDisplayMedia` is the only consent machinery the page starts, and the
  permission readback (`display-capture` — support varies per engine) is
  answered or honestly `unavailable` on both scopes, always mirrored;
- **the pixels and the cursor** — where a session does stream (a nightly
  Firefox with the processor flag, if it holds), repeat procedure 2's
  readings. Record what the engine embedded for each cursor mode and each
  surface kind.

Any observation outside the guarded paths above is a finding to report, not
a feature.

### 6. Camera out of v1: the confirmation

There is no `getUserMedia` camera path in this adapter — a camera is a
distinct surface semantics the same browser flow does not provide, and the
decision is recorded rather than left implicit. The automated canary is the
pin (the stub records any call under any name, and none occurs); this pass
confirms the visible face of the same fact. Drive every flow of procedures
1-4 on both bundles and observe: no camera light, no camera entry in any
picker, no `getUserMedia` prompt of any kind — the picker only ever offers
screens, windows and tabs, and the only call the page can start is the one
`getDisplayMedia` of an explicit open. From the console, before and after
every flow, the negative reads the same way: the adapter's code contains no
`getUserMedia` call (zero occurrences in the platform sources), so no page
behaviour can light a camera. Record anything that contradicts this as a
finding.

## Limits and diagnostic record

Record failures with the browser name, version and engine, the target and
bundle (JS IR or Wasm-JS), the operating system, the macOS Screen Recording
permission state where it applies, the surface kind chosen in the picker and
what the picker showed, the exact reproduction steps, the readbacks above,
the cursor behaviour actually observed per mode and surface kind, and all
console errors. Include for each session pass when the browser's own indicator
appeared and left, and for each revocation pass which user gesture ended it
and which closed outcome answered.

Do not infer support from one manual run, do not edit generated evidence, do
not add this charter or its observations to the validator, and do not extend
the declared engine of the register from one manual reading. Report the
observation alongside the deterministic target smoke result so that it can be
reproduced and turned into an automated test when the browser permits it.

## References

- [Capability register of this adapter](../../../../capabilities/web.md) — §2, §3.15-3.16
- [Web browser driver](../README.md) — Phase 7 limits and published availability
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md) — the static host and the target commands
- [Web Phase 6 displays/devices charter](phase-6-displays-devices.md) — the synthetic-source precedent this consent charter restates
- [Kadre design](../../../../DESIGN.md)
- [Operation contracts](../../../../OPERATION-CONTRACTS.md)
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md) — Phase 7 gate
