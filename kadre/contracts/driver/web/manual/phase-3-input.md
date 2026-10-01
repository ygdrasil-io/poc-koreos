# Web Phase 3 — Input manual charter

This is a short manual complement to the deterministic Phase 3 browser tests. Its
procedures cover the browser frontiers headless automation cannot honestly
produce: the real `deltaMode` a wheel reports per engine, trackpad momentum, a
real keyboard layout that is not US, real pen hardware, and pointer lock, which
this phase deliberately leaves to a later one. Procedure 1 is kept for a
different reason and is not one of those frontiers: it records a boundary whose
owner is the page rather than the adapter — focusability — and the fixture's own
page provides it. The charter is informative; it creates no validator evidence,
it does not change any contract status, and it is not wired into the contract
validator. It never replaces an automated proof, and nothing in it may be read as
the capability register: the register of this adapter's input features is
[capabilities/web.md](../../../../capabilities/web.md), and it declares the one
engine the automated smoke really runs.

## Scope and prerequisites

Phase 3 covers the input of an attached host-owned `HTMLElement`: the ordinary
keyboard, pointer and scroll path that the shared runtime reducer publishes on
`HostSurface.input`, plus the two `SurfaceUpdate` fields this phase activates,
`inputDefaultBehavior` and `pointerCapture`. The delivered boundaries are
normative in `DESIGN.md` §15.3 and in
[the driver's Phase 3 limits](../README.md#phase-3-input-limits); the
observations below exercise their edges against real devices. A Web session
still has `scope.windows.state.value.primary == null`, Kadre still creates no
DOM node, and the browser keyboard, pointer and wheel listeners still live on
the attached element and nowhere else.

Every procedure uses the existing public Web driver fixture selected by
`?scenario=`, exactly as the Phase 1 and Phase 2 charters do. Build both
distributions and serve the static host of
[phase-1-lifecycle.md](phase-1-lifecycle.md) before starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
# then, from the temporary static host of the Phase 1 charter:
#   http://127.0.0.1:8080/js/index.html?scenario=input-wheel
```

Run both target smokes before the manual pass
(`jsBrowserSmoke` and `wasmJsBrowserSmoke`, `--rerun-tasks`). Their
`web-input.spec.mjs` suite covers the deterministic contract: the
state-before-event order, the modifiers, the primary pointer, the one pointer
identity, the cancelled contact, the pixel and line wheel payloads, the single
focus reset, the frozen closed snapshot, both members of
`InputDefaultBehavior`, the owned capture and the touch boundary. The
procedures below may reveal an engine-specific issue, but may not be used to
fabricate contract evidence or to claim that an automated branch has passed.

| Target | Automated prerequisite |
| --- | --- |
| Kotlin/JS IR | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

The observation surface is the same for every input scenario. The fixture
attaches a host through the public API and republishes the surface's own stream
as attributes of that host, so an attribute a procedure reads is a fact a
consumer of this API can read:

| Attribute | What it carries |
| --- | --- |
| `data-kadre-input-state` | the whole `SurfaceInputState`: `rev=N keys=[code:7:4] mods=[Shift] pointers=[mouse#primary@(40,30)] touches=0` |
| `data-kadre-input-caps` | `keyboard=available pointer=available touch=unsupported gestures=unsupported:gestureinput` |
| `data-kadre-input-events` | every `InputEvent` published, in order, `;`-separated, each with its `:rev=` |
| `data-kadre-input-count` | how many events were published |
| `data-kadre-input-resets` | how many `StateReset` were published, and why (`1:focusLost`) |
| `data-kadre-input-flow-closed` | `true` once the events flow completed |
| `data-kadre-surface-state` | the committed `SurfaceState`: `attachment\|inputDefaultBehavior\|pointerCapture\|rev=N` |
| `data-kadre-pointer-motion`, `data-kadre-pointer-outside` | the position of the last `PointerMoved`, and whether it left the element's logical box |

A few scenarios install a command: dispatching a DOM event with that name on
`document` (`document.dispatchEvent(new Event("kadre-capture-confined"))`) runs
it and writes its result into a host attribute of the same name. The pointer
capture scenario also publishes `data-kadre-capture-unowned`,
`data-kadre-capture-locked` and `data-kadre-capture-confined` from the public
`HostSurface` itself. In the procedures below, `host` is always the scenario's
own element, `document.querySelector('[data-kadre-host="<scenario>"]')`.

## Procedures

### 1. A host element that is not focusable, and why the host must provide it

Open `http://127.0.0.1:8080/js/index.html?scenario=input-key` and wait for
`document.body.dataset.kadreReady === "true"`. The fixture host is a `div` that
the fixture itself made focusable (`host.tabIndex = 0`), because Kadre never
writes `tabindex` and never touches the host's DOM. Take that away and observe
what the surface does:

```js
const host = document.querySelector('[data-kadre-host="input-key"]');
host.removeAttribute("tabindex");
host.getAttribute("data-kadre-input-caps");   // keyboard=available pointer=available ...
document.activeElement === host;              // false
host.getAttribute("data-kadre-input-count");  // 0
```

Click the host to make sure the browser did not focus it, press a letter key
and an arrow key, then wait one animation frame. The keyboard observation of
`DESIGN.md` §15.3 (D7) is a boundary of the host, not a defect of the adapter:
`data-kadre-input-events` stays empty and `data-kadre-input-count` stays `0`,
while `data-kadre-input-caps` keeps reporting `keyboard=available` — the
capability describes the installed observation, not the arrival of a keystroke.
Confirm the noise was delivered to the page instead, for example by a temporary
`document.addEventListener("keydown", …)` that records the same event.

Then restore the contract the fixture's page provides:

```js
host.tabIndex = 0;
host.focus();
document.activeElement === host;   // true
```

Press the same key again: the surface now publishes the key event
(`data-kadre-input-events` starts with `key:a:pressed:mods[]:rev=`, and
`data-kadre-input-state` reports `keys=[code:7:4]`). Repeat the pass once in
`?scenario=input-pointer` for the pointer: the listeners are on the element, so
a pointer over the host is observed whether or not the element is focusable,
while a keyboard observation requires the focus the page must make possible.
The consequence of a non-focusable host is the honest silence: the surface claims
`keyboard = available` and receives nothing at all. The reason this pass is
written down rather than asserted is the owner of the boundary, not a limit of
the suite: removing `tabindex` and pressing a key is deterministic, but
focusability belongs to the page — Kadre writes no `tabindex` (D7) and the
fixture's own page makes its host focusable (`host.tabIndex = 0`) — so an
automated scenario could stage that silence only by asserting the fixture page's
own choice rather than the adapter's contract.

### 2. The real `deltaMode` of a wheel, per engine

Open `http://127.0.0.1:8080/js/index.html?scenario=input-wheel`, wait for
`data-kadre-ready`, and record what the browser really reports before reading
what the surface published:

```js
const host = document.querySelector('[data-kadre-host="input-wheel"]');
window.__kadreWheelModes = [];
host.addEventListener("wheel", (event) => window.__kadreWheelModes.push({
  deltaMode: event.deltaMode, deltaX: event.deltaX, deltaY: event.deltaY,
}));
```

Scroll over the host with a real wheel or a real trackpad, with a plain gesture
and with a scroll that carries a horizontal component, then read:

```js
window.__kadreWheelModes;                       // what this engine reported
host.getAttribute("data-kadre-input-events");   // what the surface delivered
```

The delivered payload is one of exactly three outcomes, and the reading is the
`deltaMode` the engine really reported: `ScrollDelta.Logical` for
`DOM_DELTA_PIXEL` (the payload reads `scroll:logical(x,y)`),
`ScrollDelta.Lines` for `DOM_DELTA_LINE` (the payload reads
`scroll:lines(x,y)`), and nothing at all for `DOM_DELTA_PAGE`, which this phase
names and drops rather than converting a page into pixels or lines without a
page size (`DESIGN.md` §15.3, D9). The signs are not touched: a wheel reports
positive deltas down and right, which is Kadre's own orientation.

Do this on every engine you have, and record the pair (engine, `deltaMode`,
delivered payload) for each. Chromium's own input pipeline is the engine the
automated suite runs, and it never reports a line-mode wheel, which is why the
`Lines` payload is proven by a synthetic `WheelEvent` there; an engine that
reports `deltaMode 1` for a real wheel is the only way to see a real line-mode
payload reach the model. A page-mode wheel cannot be produced by hardware at
all, so an unchanged `data-kadre-input-count` after a hand-dispatched
`new WheelEvent("wheel", { deltaMode: 2, deltaX: 0, deltaY: 2, bubbles: true })`
— dispatched *on the host element*, because no keyboard, pointer or wheel
listener exists on the document, the window or an ancestor — is a confirmation
of the declared absence, not a new observation. One engine's reading is an
observation of that engine: it is not added to the register, which declares the
Chromium revision the smoke pins.

### 3. Trackpad momentum

Keep `?scenario=input-wheel` open. Fling the trackpad so the page keeps
scrolling after the fingers have stopped, and read the published events and
their count as the tail decays:

```js
host.getAttribute("data-kadre-input-events");
host.getAttribute("data-kadre-input-count");
```

The DOM carries no phase and no momentum bit — the `NSEventPhase` and
`CGMomentumScrollPhase` facts AppKit reduces from do not exist here — so Kadre
cannot tell a momentum wheel from a user-driven one. The frontier of this
adapter substitutes the browser's own delivery granularity for that native pair:
the frontier opens for the first wheel a new animation frame delivers, and when
the `deltaMode` or the pointer's button state changes, and nowhere else. What a
manual pass can
honestly observe is the consequence: the tail of a fling is delivered as a run
of `scroll:logical(...)` events whose count keeps growing frame after frame,
never as one event that swallowed the whole fling, and two scrolls the browser
separated by a frame boundary are never summed into one. The frontier itself is
never public and appears in no attribute — a reading that claims to see it is a
reading of the fixture, not of the API. Repeat the fling with a button held to
see the frontier open on the button-state change as well, and repeat once
without momentum (a wheel notch, or a slow two-finger drag) as the control.

### 4. A real keyboard layout that is not US

Open `http://127.0.0.1:8080/js/index.html?scenario=input-key`, wait for
`data-kadre-ready`, and switch the operating system to a non-US layout
(AZERTY, QWERTZ, Dvorak, or any layout you can type on). Record the layout, then
press a key whose position and letter disagree between the layouts — on an
AZERTY keyboard, the key at the QWERTY `A` position is the one that prints `q`,
and the key that prints `a` sits where QWERTY has `Q`.

```js
host.getAttribute("data-kadre-input-state");    // keys=[code:7:4]  — the QWERTY-A position
host.getAttribute("data-kadre-input-events");   // key:q:pressed:mods[]:rev=... — this layout's own letter
```

The physical key must be the one the DOM's `code` names, never the letter the
layout prints: `webPhysicalKey` reads `KeyboardEvent.code` and maps it through
the USB HID usage table (`WebInputMapping.kt`), and `code` is the physical key
of the US layout by definition, while the logical key comes from `event.key`.
So on AZERTY the press of the key that prints `q` sits at the QWERTY `A`
position and is `code:7:4` (usage `0x04`, the HID usage of `KeyA`) with the
logical key this layout produced, while the key that prints `a` sits at the
QWERTY `Q` position and is `code:7:20` (usage `0x14`, `KeyQ`) with the logical
key `a`. The `code`→HID mapping is unchanged by the layout. Press the same
physical key again after switching layouts mid-session without reloading and
confirm the physical key readback is identical while the logical key follows the
layout.

Two further readings belong to this procedure. A code the HID table does not
enumerate is not lost: it becomes `PhysicalKey.Unidentified` with the sanitised
`code` as its native token, or with no token at all when the `code` is not a
stable ASCII identifier, and the fixture renders both through
`"unidentified:$nativeCode"` (`WebDriverFixture.kt:866`) — so the second case
reads `unidentified:null` in `data-kadre-input-state`, not a bare
`unidentified:`. And `repeat` is the browser's own flag on a press only, so
holding a key produces repeated presses and exactly one release. Neither of them
depends on the layout, and both are already covered without a browser by
`WebInputMappingTest`.

One more reading belongs here because it is the *other* half of layout
independence: the suppressed document-scroll set is matched on the **logical**
key, not on the physical one. `webInputCategory` requires
`stimulus.logicalKey is LogicalKey.Named` and membership of
`WEB_DOCUMENT_SCROLL_KEYS` (`WebInputTranslation.kt:186-197`), because the
browser's own default is defined by the key the layout produced. To watch it,
open `?scenario=input-default-behavior` — its page is scrollable and it installs
both behaviour commands — switch the surface with
`document.dispatchEvent(new Event("kadre-behavior-suppress"))`, and press the
space bar and then an arrow key. Both are still read as their own `NamedKey` on
the layout in hand (the payload reads `key:Space:pressed:…` and
`key:ArrowDown:pressed:…`, since a `LogicalKey.Named` is rendered by its name),
both keep the physical readback their `code` gives them, and the document does
not scroll. A layout that moves the letter keys around changes neither logical
name, which is why this is the reading to take on the same non-US layout as
above rather than a substitute for it.

### 5. Real pen hardware

Open `http://127.0.0.1:8080/js/index.html?scenario=input-pointer-multi` and use
a real stylus on a real digitizer (a tablet, or a laptop with a pen display).
Hover over the host, then press, move and release:

```js
host.getAttribute("data-kadre-input-state");    // pointers=[pen#primary@(60,40):pen]
host.getAttribute("data-kadre-input-events");   // enter:pen@(...,...); button:primary:pressed@...; move:pen@...:d=(...)
```

`pointerType` is the browser's own word and the only source of the pointer kind,
so a real pen must be delivered as `PointerKind.Pen`, and the pen members
(`pressure`, `tiltX`, `tiltY`, `twist`, `tangentialPressure`) are carried only
when they fall inside their declared domains — a value outside a domain is
dropped rather than transmitted or thrown on, which the automated suite proves
without hardware. The fixture marks that a pen state exists (`:pen`) rather than
republishing its members, so what this pass reads is the kind and the pen's
presence; the exact member values are read from the consumer's own
`SurfaceInput.state`. Record the pen model and what it reports. A digitizer that
reports `pointerType == "touch"` for its stylus is the declared boundary of this
phase and not a failure: the port refuses the touch kind before acting, so
nothing is published, no `TouchState` is created and `InputCapabilities.touch`
keeps saying `unsupported` on the live surface this pass reads (the register's
`touch` row gives its two absent states, the second being the overflow-terminal
one this procedure does not stage). Also compare the two `pointerType` values your
hardware actually produces (hover versus contact, eraser versus tip) against the
kind readback; a pen-specific behaviour the automated suite cannot stage — a
pressure ramp, an eraser end, a barrel button — is exactly what this procedure
is for.

### 6. Pointer lock, which this phase leaves to a later one

Pointer lock needs a transient user activation and belongs to
`InteractionAction.LockPointer` (`DESIGN.md` §9.6), so no member of this target
locks a pointer: no `requestPointerLock` call exists in either port. Open
`http://127.0.0.1:8080/js/index.html?scenario=input-pointer-capture`, wait for
the readiness attribute, and ask the public surface for the mode anyway:

```js
document.dispatchEvent(new Event("kadre-capture-locked"));
host.getAttribute("data-kadre-capture-locked");   // partiallyApplied[pointerCapture=unsupported:updatesurface]
host.getAttribute("data-kadre-surface-state");    // attached|hostdefault|none|rev=N — unmoved
```

The refusal is a rejected field, the state did not move, and the browser was
never asked. For the contrast, lock the page by hand and watch what Kadre does
with the resulting observations — the call needs the transient activation the
API requires, so make it from a real click (or from the console of a page you
have just clicked), and remember that the browser emits its own lock prompt and
that `Esc` ends the lock:

```js
document.body.requestPointerLock();
// the browser now owns the pointer: move it and read what the surface published
host.getAttribute("data-kadre-pointer-motion");   // the position the browser reported, if any
host.getAttribute("data-kadre-surface-state");    // still none
```

The lock is the page's, not the surface's: Kadre publishes no capture it was not
asked for, and its pointer observations are only what the browser delivered for
this element's own `pointermove`. What the browser reports for a confined or
hidden pointer during a page-owned lock is that page's reading and not a Kadre
promise; this phase claims nothing about it. What a manual pass records is that
this phase neither locks a pointer nor claims to, and that a lock the page took
by itself leaves the published capture at `none`.

**Discharged by phase 4.** The interaction path this procedure deferred is
delivered: the Web surface installs the common interaction engine and publishes
`handlerInteractions = {EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer}`,
the port now carries the `requestPointerLock`/`exitPointerLock` primitives with
their one-shot terminal listeners, and the transient-activation frame the API
requires is the DOM callback the dispatch runs in. The automated smoke proves
that path end-to-end with a real trusted click
(`web-interaction-fullscreen`, `web-interaction.spec.mjs`); the real-screen
proof of the pointer-lock primitive — confined movement, `Esc` — is
[phase-4-interactions.md](phase-4-interactions.md), procedure 2, which also
records the boundary that remains: the delivered fixture's handler requests
fullscreen only, so a Kadre-*driven* lock on a real screen still awaits a
fixture command selecting the action.

### 7. Two pointers at once: the DOM gives two, the model keeps one

The runtime keeps **one pointer identity per surface**: every `PointerEvent`
whose `pointerType` is `mouse` or `pen` feeds that single identity, with the
kind of the event in hand (D11). Open
`http://127.0.0.1:8080/js/index.html?scenario=input-pointer-multi` and drive the
mouse over the host and a pen over it at the same time — or, if you have only
one device, hold the real mouse still and deliver a pen observation by hand:

```js
host.dispatchEvent(new PointerEvent("pointermove", {
  pointerId: 7, pointerType: "pen", isPrimary: true, button: -1, buttons: 0,
  clientX: 60, clientY: 40, pressure: 0.5, bubbles: true,
}));
await new Promise(requestAnimationFrame);
host.getAttribute("data-kadre-input-state");    // one entry, with the kind of the last event
host.getAttribute("data-kadre-input-events");
```

There is exactly one entry in `pointers=[…]`, and the kind it carries is the kind
of the last event observed, never two invented pointers. Confirm as well that
the merge does not leave a button stuck: after the merged identity's last press
is released the entry shows no button (`pointers=[pen#@(60,40):pen]` while the
pen is the current kind, `pointers=[mouse#@(60,40)]` once the mouse moves again
— the two forms `web-input-pointer-multi` asserts). A second concurrent pointer
from a device the DOM reports separately (a touch contact, for instance)
produces nothing at all, because the touch kind is refused whole. Combinations
this pass should record, because hardware makes them easy and the automated
suite cannot stage them: mouse and pen held simultaneously, a pen re-entering
while the mouse is outside, and a pen lift that reports `pointerleave` while the
mouse is still over the element.

## Limits and diagnostic record

Record failures with the browser name, version and engine, the target and
bundle (JS IR or Wasm-JS), the operating system keyboard layout in use, the
pointer or pen model, the exact reproduction steps, the readbacks above, and all
console errors. Include for each wheel observation the `deltaMode` the engine
reported and the payload the surface delivered, whether the gesture was a wheel
notch or a trackpad fling, and the event count before and after the fling.

Do not infer support from one manual run, do not edit generated evidence, do not
add this charter or its observations to the validator, and do not extend the
declared engine of the register from one manual reading. Report the observation
alongside the deterministic target smoke result so that it can be reproduced and
turned into an automated test when the browser permits it.

## References

- [Capability register of this adapter](../../../../capabilities/web.md)
- [Web browser driver](../README.md) — Phase 3 limits and published availability
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md) — the static host and the target commands
- [Web Phase 2 surface charter](phase-2-surface.md)
- [AppKit Phase 4 input design](../../../../APPKIT-PHASE-4-INPUT-DESIGN.md) — the native phase/momentum frontier this adapter substitutes
- [Kadre design](../../../../DESIGN.md) — §15.3, §9.6
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md)
