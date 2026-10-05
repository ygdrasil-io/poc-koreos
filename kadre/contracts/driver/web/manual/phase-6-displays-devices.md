# Web Phase 6 — Displays, devices and gamepads manual charter

This is a short manual complement to the deterministic Phase 6 browser tests.
Its procedures cover the browser frontiers headless automation cannot honestly
produce: a real hardware gamepad connecting and disconnecting through the
operating system, an actual rumble felt by the hand that holds the pad,
Chromium's privacy gate (real pads stay invisible to a page until the user
activates it), a real multi-display setup beside the inventory the adapter
deliberately does not enumerate beyond, the insecure-context matrix beyond the
fake-hostname trick the automated project uses, and the `vibrationActuator`
behaviour of Firefox/WebKit, which the pinned Chromium smoke cannot speak for.
It is informative; it creates no validator evidence, it does not change any
contract status, and it is not wired into the contract validator. It never
replaces an automated proof, and nothing in it may be read as the capability
register: the register of this adapter's display, device and gamepad features
is [capabilities/web.md](../../../../capabilities/web.md) (§2, §3.12-3.14), and
it declares the one engine the automated smoke really runs.

## Scope and prerequisites

Phase 6 covers the display inventory, the device/gamepad inventory and routing,
and the gamepad effects of an attached host-owned element. The delivered
boundaries are stated in the phase-6 section of
[the implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md), in
[the driver's Phase 6 limits](../README.md#phase-6-displays-devices-and-gamepad-limits)
and in the register sections cited there; the observations below exercise
their edges against real hardware, real screens and real deployments. Kadre
still creates no DOM node, still triggers no permission prompt — the
automated no-implicit-prompt sentinel is the pin, and every procedure below
must keep it true.

Every procedure uses the existing public Web driver fixture selected by
`?scenario=` (`display`, `devices`, `gamepad-effects`). Build both
distributions and serve the static host of
[phase-1-lifecycle.md](phase-1-lifecycle.md) before starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
# then, from the temporary static host of the Phase 1 charter:
#   http://127.0.0.1:8080/js/index.html?scenario=devices
```

Run both target smokes before the manual pass
(`jsBrowserSmoke` and `wasmJsBrowserSmoke`, `--rerun-tasks`). Their suites
cover the deterministic contract: `web-display.spec.mjs` drives real resizes
and real dpr changes through CDP, and `web-devices.spec.mjs` /
`web-gamepad-effects.spec.mjs` drive the synthetic gamepad source
(`gamepad-stub.mjs`) that patches the poll the hub reads — the D10 precedent,
stated in their headers: Chromium cannot inject a real gamepad, so what the
smokes prove is what the runtime publishes about whatever the poll reports.
The procedures below may reveal an engine-specific issue, but may not be used
to fabricate contract evidence or to claim that an automated branch has passed.

| Target | Automated prerequisite |
| --- | --- |
| Kotlin/JS IR | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

The observation surface is the same for every scenario. The fixture attaches
a host through the public API and republishes what the managers publish as
attributes of that host, so an attribute a procedure reads is a fact a
consumer of this API can read:

| Attribute | Scenario | What it carries |
| --- | --- | --- |
| `data-kadre-display-manager` | `display` | the whole `DisplayManagerState` — revision, `enumerated`, the primary and display count, the enumeration capability (`rev=1:enumerated:primary=0:displays=1:enumeration=supported`) |
| `data-kadre-display-display` | `display` | the primary display's `DisplayState` — `type=HostViewport`, the physical bounds and work area, the scale factor, the one mode with its `refreshRateHz` (always `none`) and `bitDepth` |
| `data-kadre-display-events` | `display` | every `DisplayEvent` published, in order, each naming the manager revision it was stamped with |
| `data-kadre-devices-manager` | `devices`, `gamepad-effects` | the whole `DeviceManagerState` — revision and the `enumerated` device/gamepad counts |
| `data-kadre-devices-events` | `devices`, `gamepad-effects` | every `DeviceLifecycleEvent` in order (`added:g0@1`, `removed:g0@2`) |
| `data-kadre-gamepad-<ordinal>` | `devices`, `gamepad-effects` | each enumerated pad's whole `GamepadSnapshot` — descriptor, connection, routing, controls, effects capability, revision |
| `data-kadre-gamepad-<ordinal>-events` | `devices`, `gamepad-effects` | every `GamepadEvent` that pad published, in order |
| `data-kadre-gamepad-effect`, `-effect-result` | `gamepad-effects` | the effect session's state flow (`none`, `playing`, `terminated:...`) and the admission's answer (`success`, or the encoded failure) |
| `data-kadre-gamepad-raw` | `gamepad-effects` | the answer of `requestRawInput` (`unsupported:rawinputaccess`) |

In the procedures below, `host` is the scenario's own element,
`document.querySelector('[data-kadre-host="<scenario>"]')`, and a fixture
command is dispatched with
`document.dispatchEvent(new Event("<command>"))` on `document` — the commands
`kadre-display-request`, `kadre-stop-display`, `kadre-stop-devices`,
`kadre-gamepad-play-dual-rumble`, `kadre-gamepad-play-trigger`,
`kadre-gamepad-play-localized`, `kadre-gamepad-stop-effect` and
`kadre-gamepad-raw-input` exist.

## Procedures

### 1. A real hardware gamepad: the OS connect/disconnect and the privacy gate

Open `http://127.0.0.1:8080/js/index.html?scenario=devices` (replace `js` with
`wasm` for the Wasm-JS bundle) in a **non-headless** Chromium, wait for
`data-kadre-ready`, and focus the host. With no pad connected, the inventory is
the honest empty one:

```js
const host = document.querySelector('[data-kadre-host="devices"]');
host.getAttribute("data-kadre-devices-manager");   // rev=0:enumerated:devices=0:gamepads=0
host.getAttribute("data-kadre-devices-events");    // (empty)
```

**The privacy gate.** Without touching the pad, read the browser's own poll
from the console:

```js
navigator.getGamepads().filter(Boolean).length;    // 0 — even with the pad already on and paired
```

Chromium keeps real pads invisible to `getGamepads()` until the user interacts
with the page — press a pad button or move a stick, then read again: the poll
now reports the pad, and the hub's next animation-frame diff publishes it
through the public inventory. This is exactly the gate the automated suite
cannot stage (its synthetic source bypasses it), and the manual pass is where
the delivered behaviour is recorded: the poll-driven inventory states the
gate's answer, whatever it is, and invents nothing in either direction. Record
the browser, the OS and the moment of activation.

**The OS connect/disconnect.** With the page activated, unplug or power off
the pad — the OS-level disconnection, not a DOM mutation: the browser fires
`gamepaddisconnected` at the window, the hub re-polls immediately, and the
inventory shrinks with one `GamepadRemoved` at the next manager revision while
the pad's own snapshot dies disconnected, suspended, neutral:

```js
host.getAttribute("data-kadre-devices-events");    // added:g0@1;removed:g0@2
host.getAttribute("data-kadre-gamepad-0");         // ...:connection=disconnected:routing=suspended:controls=buttons=[...zeroes...]
```

Plug it back: a reconnect is a new connection — a fresh descriptor frozen from
the reconnect poll, a fresh `GamepadAdded`, a fresh `GamepadId` (the model
never resurrects a handle that reached its terminal state). Do the same with
the page **not** activated (a fresh tab navigated directly to the scenario,
pad already paired): the poll answers nothing until the activation, and the
inventory stays enumerated, empty and complete — never `Unavailable`.

### 2. Actual rumble on real hardware

Open `?scenario=gamepad-effects`, activate the page with the pad (procedure 1)
so the pad is discovered and routed
(`data-kadre-gamepad-0` reads `:routing=routed:effects=supported[kinds=...]`),
then play the delivered dual-rumble:

```js
document.dispatchEvent(new Event("kadre-gamepad-play-dual-rumble"));
host.getAttribute("data-kadre-gamepad-effect-result");  // success
host.getAttribute("data-kadre-gamepad-effect");         // playing
```

What the automated suite proves about this call is the dictionary shape and
the governed path; what this pass adds is the physical fact — the pad actually
rumbles with the strong/weak magnitudes the fixture requested (0.75/0.25), for
the requested 5 seconds, and the hand can tell strong from weak. Press
`kadre-gamepad-stop-effect` mid-flight: the rumble stops on the actuator
reset. Then verify the recorded limits on the same pad: an effect launched and
left alone ends by itself when the duration elapses (the browser stops it — no
Kadre timer), and a stop that arrives after that is the reset of an
already-quiet actuator. If the pad's actuator declares `trigger-rumble` in its
own `effects` list (newer Chromium reports the list the pad supports), the
`kadre-gamepad-play-trigger` command carries its trigger magnitudes; a pad
that only declares `dual-rumble` refuses it `failure:invalidRequest:effect`
before any actuator call. Record the pad model, the browser and what the
`effects` list of the real actuator contained — that list is the one fact the
synthetic source substitutes, and this pass records the real one.

### 3. Real multi-display, and the Window Management API documented as unsupported

Kadre never enumerates the displays behind the window: the Window Management
API (`navigator.getScreenDetails`) is deliberately never called — a
prompt-forbidding permission gate, top-level-only, and inconsistent across
engines — and the delivered inventory is exactly the `HostViewport` fallback
(`Enumerated(primary = viewport, displays = listOf(viewport))`), unconditional
for an attached session. This procedure records what that means on a real
multi-display machine. Open `?scenario=display` on a host with two or more
physical screens and read:

```js
host.getAttribute("data-kadre-display-manager");   // rev=1:enumerated:primary=0:displays=1 — one display, always
host.getAttribute("data-kadre-display-display");   // type=HostViewport:...:bounds=0,0,<round(w·dpr)>,<round(h·dpr)>...
```

Then move the browser window to the other screen and resize it there: the
inventory republishes (one `Changed` per resize) with the new viewport's own
measurement, still exactly one display — the fallback follows the browsing
context, never the desk. Check the negative too, from the console, before and
after every move: `navigator.getScreenDetails` was never called by the page
(the browser would have prompted; no prompt appears), and no second display
ever appears in `data-kadre-display-manager` regardless of how many screens
are attached. A host that needs real multi-display state uses its own Window
Management integration; what Kadre publishes about the second screen is
nothing, and that nothing is the documented contract. Record the display
arrangement, the dpr of each screen and the republish count observed.

### 4. The insecure-context matrix beyond the fake-hostname trick

The automated insecure-context scenario runs in a dedicated Playwright project
whose launch argument maps the fixture's insecure name onto the local server
(`--host-resolver-rules=MAP insecure.kadre.invalid 127.0.0.1`); it asserts
observables only — `isSecureContext` false, the pad discovered, the effects
capability `Unsupported`, zero permission prompts. This procedure covers the
deployments a real application meets and the automation does not. For each row,
serve the built `js`/`wasm` distributions from a plain http server on the
network address named, open `?scenario=devices` (then `?scenario=gamepad-effects`),
and read `window.isSecureContext`, the pad discovery (with the page activated
per procedure 1), the pad's `data-kadre-gamepad-0` effects cell, and the
browser's pad poll:

| Deployment | Expected reading |
| --- | --- |
| `http://<LAN-IP>:8080` (e.g. `192.168.x.x`) | `isSecureContext` false; `getGamepads()` answers nothing on a real Chromium — the inventory stays `enumerated:devices=0:gamepads=0`; no prompt ever appears |
| `http://localhost:8080` | `isSecureContext` **true** (localhost is a potentially trustworthy origin) — the pad behaves exactly as in procedure 1; this is the edge the fake-hostname trick deliberately avoids |
| `http://127.0.0.1:8080` | same as `localhost` — trustworthy origin |
| `file:///.../index.html` | origin null; the browser's own answer is the record — read `isSecureContext` and the poll before claiming anything |

The invariant across every row is the delivered one: the inventory is always
`Enumerated` — empty and complete where the browser exposes nothing, never
`Unavailable` dressed up — and no permission prompt is ever triggered by
enumeration or readback. The effects capability of a pad that does reach a poll
in an insecure context (only the synthetic source can stage that, which is what
the automated scenario does) is
`Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.GamepadEffect))`;
record whether any real deployment ever exhibited a pad there (none is
expected on Chromium). Record the browser, the deployment and the exact
readings per row.

### 5. Firefox/WebKit `vibrationActuator` behaviour

The pinned smoke runs Chromium only, and the register declares one engine; the
other two differ in exactly the places the seam guards, and this procedure
records which guard fired. Load `?scenario=devices` and
`?scenario=gamepad-effects` in a **Firefox** and a **WebKit** build with a
real pad attached (procedure 1's activation applies there too), and read:

- **the actuator** — `vibrationActuator` is absent on both engines today: the
  guarded extraction answers `null` (`JsWebGamepadDom.kt:128-137` and the Wasm
  mirror), and the pad's effects cell reads
  `effects=unsupported:gamepadeffect`. A dual-rumble request is then refused
  `failure:invalidRequest:effect` before anything reaches the browser — the
  honest Unsupported of a missing primitive, not a guessed one;
- **the connection events** — both engines implement the navigator as an event
  target and may fire `gamepadconnected`/`gamepaddisconnected` there; the seam
  registers at the window *and* at the navigator, each where its target accepts
  listeners, so the announcement is heard either way and a browser that fires
  both delivers one redundant poll and nothing else. Verify one connect and one
  `GamepadAdded` per plug-in, never two;
- **the dpr query** — Firefox is the engine where a dpr change may not fire a
  `resize`; the resolution query re-registers itself on each fire, and the dpr
  is re-read at every measurement (the phase-2 posture). A real browser zoom on
  the `?scenario=display` page must republish the inventory at the new ratio
  (`scale=` follows the zoom, bounds follow `round(w·dpr)`).

Record engine, version, pad and the exact cells observed; any observation
outside the guarded paths above is a finding to report, not a feature — the
register declares the engine the automated smoke really runs, and a second
engine becomes a declared minimum only through its own evidence, never through
a manual reading.

## Limits and diagnostic record

Record failures with the browser name, version and engine, the target and
bundle (JS IR or Wasm-JS), the operating system, the display arrangement, the
pad model and what its real `effects` list reported, the exact reproduction
steps, the readbacks above, and all console errors. Include for each gamepad
pass whether the page had been user-activated before the poll reported the
pad, for each effect pass what the hand actually felt and when the browser
itself stopped the effect, for each display pass the screens attached and
their dpr, and for each insecure row the exact deployment and reading.

Do not infer support from one manual run, do not edit generated evidence, do
not add this charter or its observations to the validator, and do not extend
the declared engine of the register from one manual reading. Report the
observation alongside the deterministic target smoke result so that it can be
reproduced and turned into an automated test when the browser permits it.

## References

- [Capability register of this adapter](../../../../capabilities/web.md) — §2, §3.12-3.14
- [Web browser driver](../README.md) — Phase 6 limits and published availability
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md) — the static host and the target commands
- [Web Phase 5 text-input charter](phase-5-text-input.md) — the observation-surface pattern this charter follows
- [Kadre design](../../../../DESIGN.md)
- [Operation contracts](../../../../OPERATION-CONTRACTS.md)
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md) — Phase 6 gate
