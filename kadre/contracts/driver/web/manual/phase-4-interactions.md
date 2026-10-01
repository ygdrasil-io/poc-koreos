# Web Phase 4 — Interactions and windows manual charter

This is a short manual complement to the deterministic Phase 4 browser tests.
Its procedures cover the browser frontiers headless automation cannot honestly
produce: a real user-activated fullscreen with the browser's own `Esc` exit, a
real pointer lock with the pointer hidden and confined, the transient-activation
and token rules a page console can actually observe, and the real visible popup
that the provider scenarios only reach through a headless automation handle.
It is informative; it creates no validator evidence, it does not change any
contract status, and it is not wired into the contract validator. It never
replaces an automated proof, and nothing in it may be read as the capability
register: the register of this adapter's interaction and window features is
[capabilities/web.md](../../../../capabilities/web.md) (§2 bottom rows,
§3.7-3.8), and it declares the one engine the automated smoke really runs.

## Scope and prerequisites

Phase 4 covers the interaction seam — the synchronous handler whose token the
common runtime engine owns, dispatching fullscreen and pointer-lock primitives
from the phase 3 `pointerdown`/`keydown` listeners inside the frame of transient
activation — and the host windows that a `WebWindowProvider` opens: a child
session attached through the ordinary path in a browsing context the host
prepared. The delivered boundaries are normative in `DESIGN.md` §9.6, in the
`WebWindowProvider` rows of `OPERATION-CONTRACTS.md` §4, and in
[the driver's Phase 4 limits](../README.md#phase-4-interaction-and-window-limits);
the observations below exercise their edges against real screens and real
windows. Kadre still creates no browsing context, no popup and no DOM node: the
provider is handed an element the host already created, and the popup of the
provider scenario exists **before** any Kadre call
(`WebDriverFixture.kt:658-682`).

Every procedure uses the existing public Web driver fixture selected by
`?scenario=`, exactly as the Phase 1–3 charters do. Build both distributions and
serve the temporary static host of
[phase-1-lifecycle.md](phase-1-lifecycle.md) before starting:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserDistribution :kadre:contracts:driver:web:wasmJsBrowserDistribution
# then, from the temporary static host of the Phase 1 charter:
#   http://127.0.0.1:8080/js/index.html?scenario=web-interaction
```

Run both target smokes before the manual pass
(`jsBrowserSmoke` and `wasmJsBrowserSmoke`, `--rerun-tasks`). Their
automated suites cover the deterministic contract: `web-interaction.spec.mjs`
clicks the host for real and asserts the closed set of honest outcomes —
`committed`, or `rejected:platformFailure:web:fullscreen:refused` — recording
the outcome this headless Chromium gave (the run of this phase observed
`committed` on both targets, so the headless browser honoured the primitive);
`web-window-provider.spec.mjs` proves the seven provider scenarios of `BCK-001`
against the popup the fixture itself opened; `web-host-facade.spec.mjs` proves
the five `INT-003` scenarios through the delivered `windowProvider` option. The
procedures below may reveal a browser-specific issue, but may not be used to
fabricate contract evidence or to claim that an automated branch has passed.

| Target | Automated prerequisite |
| --- | --- |
| Kotlin/JS IR | `rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke --rerun-tasks` |
| Kotlin/Wasm-JS | `rtk ./gradlew :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks` |

Two observation surfaces are used below, both attributes of the fixture's own
host element or of the elements the fixture prepared — facts a consumer of this
API can read:

| Attribute | Scenario | What it carries |
| --- | --- | --- |
| `data-kadre-interaction-armed` | `web-interaction` | `true` once the handler is installed — the installation barrier the spec waits for |
| `data-kadre-interaction-fullscreen` | `web-interaction` | the outcome of the handler's latest request: `committed`, `rejected:<failure encoding>` (`rejected:platformFailure:web:fullscreen:refused` for a browser refusal), `install-failure:<encoding>` |
| `data-kadre-window-caps` | `window-provider` | the manager's request-window capability, `supported[OpenedInNewSession]` with a provider |
| `data-kadre-window-outcome` | `window-provider` | the encoded terminal outcome of the request, e.g. `opened-in-new-session:<session hash>` |
| `data-kadre-session-hash`, `data-kadre-session-reason`, `data-kadre-session-lifecycle` | both | the session identity each session records on the element its host prepared — the requester's host for the initial attachment, the offered element for the child; the child's reason reads `additionalHostRequested` |

In the procedures below, `host` is always the scenario's own element,
`document.querySelector('[data-kadre-host="<scenario>"]')`, and a fixture
command is dispatched with
`document.dispatchEvent(new Event("<command>"))` on `document`.

## Procedures

### 1. Real fullscreen with a real user activation, and the browser's own exit

Open `http://127.0.0.1:8080/js/index.html?scenario=web-interaction` (replace
`js` with `wasm` for the Wasm-JS bundle) in a **non-headless** browser and wait
for `document.body.dataset.kadreReady === "true"`. Confirm the handler is
installed (`host.getAttribute("data-kadre-interaction-armed") === "true"`), then
**click the host with the mouse** — a real press, the trusted event whose
transient activation is the entire authority the primitive needs. Read the
outcome:

```js
const host = document.querySelector('[data-kadre-host="web-interaction"]');
host.getAttribute("data-kadre-interaction-fullscreen");   // committed
document.fullscreenElement === host;                      // true — the browser took the element fullscreen
```

`committed` is published **only** at the browser's own `fullscreenchange`, never
at the moment the request was made: the outcome is the browser's confirmation,
not Kadre's optimism. Now press **Échap** (`Esc`) and observe what Kadre does
with an exit it did not ask for:

```js
// after Esc:
document.fullscreenElement;                               // null — the browser exited on its own
host.getAttribute("data-kadre-interaction-fullscreen");   // still "committed" — unchanged
```

The `Esc` exit publishes **nothing**: no outcome, no event, no state reset — an
outcome exists only for a request an interaction admitted, and the browser's own
exit is not one (`DESIGN.md` §9.6: the registration correlates each request id
with its terminal result; it publishes nothing else). The frozen last outcome is
also the honest reading of the automated assertion, which pins exactly this:
the attribute carries the closed set, one value per click. Click the host again:
the document re-enters fullscreen and a fresh `committed` replaces the old one —
the authority is per-event, one token per dispatch, never a sticky grant.

Repeat the pass with the page in a state where the browser can refuse — for
example after the document already lost its activation window (click elsewhere
first, then drive the request from a *synthetic* dispatch, procedure 3), or in
a browser profile whose fullscreen prompt is denied — and record the refusal:
`rejected:platformFailure:web:fullscreen:refused`, the one honest code, whatever
the browser's own reason was. A pass that observes a refusal is not a failure of
the adapter: it is the browser being the arbiter, exactly what the register
records.

### 2. Real pointer lock, confined movement, and `Esc`

The delivered fixture's handler requests fullscreen only — there is no command
that switches its action — so a Kadre-driven `LockPointer` cannot be staged from
a real screen with the fixture alone; what a real screen can prove is the
primitive itself and what the surface publishes around it. The pointer-lock
request needs a transient activation, so make it from a **real click** on the
page: open `?scenario=input-pointer-capture`, wait for readiness, then run this
from the console *immediately after clicking the host* (the click's activation
is still held):

```js
document.body.requestPointerLock();
// the browser now owns the pointer: it disappears, and its movements are confined
```

Move the mouse — the movements arrive with `movementX`/`movementY`, the pointer
is hidden — and read what the surface published:

```js
const host = document.querySelector('[data-kadre-host="input-pointer-capture"]');
host.getAttribute("data-kadre-input-state");    // the pointer the browser delivered, position inside the element
host.getAttribute("data-kadre-surface-state");  // attached|hostdefault|none|rev=N — the published capture stays none
```

The published `pointerCapture` stays `none` throughout: the lock is the page's,
and `PointerCaptureMode.Locked` is deliberately outside
`SurfaceCapabilities.pointerCapture` — on this phase's Web it is reachable only
as `InteractionAction.LockPointer` (the register's `pointerCapture` row and
§3.7). Press **Échap** to end the lock: the browser exits, and what the surface
publishes afterwards is the browser's own resumed pointer reporting, not a
Kadre claim. What this procedure records for a real screen is exactly what
phase 3's deferred item asked to be proven once the interaction path existed:
the primitive, its activation requirement and its `Esc` exit, observed on a real
browser — while the Kadre-driven path (`LockPointer` → the port's
`requestPointerLock` → the one-shot terminal listeners) is the identical seam
the automated smoke proves end-to-end with the fullscreen primitive on both
targets. A future fixture command selecting the action is the one extension a
Kadre-driven real-screen pass would need; this charter stages none.

### 3. The token rules a page console can observe — and the one it cannot

The interaction engine's admission rules live in the common runtime; from the
page, only their *consequences* are observable, through the fixture's outcome
attribute. Keep `?scenario=web-interaction` open.

**The browser is the arbiter (trusted versus untrusted).** Dispatch a synthetic
press from the console — the listener fires, the interaction is dispatched, the
handler requests the primitive, and the browser refuses it because an untrusted
event carries no transient activation:

```js
host.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true }));
// then, one animation frame later:
host.getAttribute("data-kadre-interaction-fullscreen");
// rejected:platformFailure:web:fullscreen:refused
```

This is the ruling the register records (§3.7, item 6): Kadre read no
`navigator.userActivation` and did not pre-refuse — the request reached the
browser, and the browser's own refusal is the answer, delivered as the one
honest `refused` outcome. A second-guessing adapter would have answered a Kadre
invented code instead; here the terminal outcome and the browser's verdict are
the same fact.

**Single-use per frame.** While a real click is still in the handler's frame,
the token is single-use — the fixture's handler requests exactly once per
dispatch, so a second request inside the same callback is not observable here;
what the console can confirm is that each real click produces exactly one
outcome (one `committed` per click, never two), which is the same
serialisation: one token, one request, one terminal.

**`Expired`, honestly out of reach of this page.** The rule itself — a context
retained past its callback is refused
`InteractionRequired(Expired)` — is **not** observable on this fixture page: the
delivered handler never retains its `InteractionContext`, and the page console
cannot hold a Kotlin context. No procedure below stages it, and a reading that
claims to see `rejected:interactionRequired:expired` in
`data-kadre-interaction-fullscreen` on this page would be a reading of a
fixture that does not exist. The rule is proven without a browser, at the seam
where the context is actually retained, by
`WebInteractionSurfaceTest.aRetainedContextIsRefusedExpiredAfterTheHandlerReturns`
(and, on the common engine,
`RuntimeInteractionHandlerCommonTest.duplicateRetainedExpiredAndUnsupportedRequestsFailWithoutCallingNativeCode`);
were a fixture scenario to retain and re-request, the outcome attribute is where
the refusal would land, encoded `rejected:interactionRequired:expired`.

### 4. The real popup: a child session in a window the host owns

The provider scenarios run headlessly against a popup the fixture opened before
any Kadre call; on a real screen the same scenario shows the window itself and
makes the host/child split visible. Open
`http://127.0.0.1:8080/js/index.html?scenario=window-provider` in a non-headless
browser and wait for readiness. The browser opened a real popup window
(`kadre-provider-window`, `about:blank`) **before** the scenario attached
anything — watch the window switcher: it is a real window, not an automation
handle — and the fixture attached the requester to `host` in the opener.

Ask the requester for a window (the default provider behaviour answers with the
element the popup already contains):

```js
const host = document.querySelector('[data-kadre-host="window-provider"]');
document.dispatchEvent(new Event("kadre-request-window"));
host.getAttribute("data-kadre-window-caps");      // supported[OpenedInNewSession]
host.getAttribute("data-kadre-window-outcome");   // opened-in-new-session:<child session hash>
```

Then look **in the popup's own console** — DevTools attached to the popup
window, not the opener — and read what the child session wrote onto the element
its host prepared:

```js
const offered = document.querySelector('[data-kadre-provider-host]');
offered.getAttribute("data-kadre-session-hash");    // the same hash the opener's outcome named
offered.getAttribute("data-kadre-session-reason");  // additionalHostRequested — the launch identity of the request that caused it
offered.getAttribute("data-kadre-session-lifecycle"); // a live lifecycle of its own, foreground-active while the popup is focused
```

The hash equality is the whole claim: the session whose id the outcome carried
is the session running in the popup, launched `AdditionalHostRequested` with
the originating request, through the ordinary attach path — no second lifecycle,
no shared scope. Confirm the duplicate-owner rule the real window makes easy to
stage: dispatch `kadre-request-window` a second time with the default behaviour
(the same prepared element is offered again) and read
`data-kadre-window-outcome` — `rejected:alreadyInUse:host`: the element a live
session owns is refused, and no second owner exists across contexts. What this
real-screen pass **cannot** stage is the requester's *scope* closure: every
scope a delivered provider hands over lives in the opener's own Kotlin runtime,
so closing the opener tab is a `pagehide` of the whole module — the boundary
`DESIGN.md` §15.3 records, where every session of the page ends — and not a
scope closure. The independence the contract promises (the requester's scope
closure never reaching the child, because the child's scope is never its
descendant) is the automated proof's; the record below notes what the window
did. Record also the refusals the real window makes visible: run the failing
provider behaviours from the opener's console
(`document.dispatchEvent(new Event("kadre-provider-same-document"))`, then
`kadre-request-window` again — likewise `kadre-provider-disconnected`,
`kadre-provider-no-context`, `kadre-provider-invalid-scope`,
`kadre-provider-throwing`) and read one encoded rung of the ladder per pass in
`data-kadre-window-outcome`: `rejected:invalidRequest:element.ownerDocument`,
`rejected:invalidRequest:element`, `rejected:invalidRequest:element.ownerDocument`,
`rejected:invalidRequest:parentScope`,
`rejected:platformFailure:web:WebWindowProvider:callback-exception` — the
provider's own exception, captured and named, never an escape into the page.

## Limits and diagnostic record

Record failures with the browser name, version and engine, the target and bundle
(JS IR or Wasm-JS), the operating system and window manager in use, the exact
reproduction steps, the readbacks above, and all console errors. Include for
each fullscreen or pointer-lock observation whether the primitive was committed
or refused and what the browser's own UI did (prompt, exit animation, `Esc`
behaviour), and for the popup pass whether the browser allowed the
script-initiated `window.open` at all — the automated scenarios assume the
runner's default `--disable-popup-blocking`, and a real profile may not grant
it (see [the driver README](../README.md)).

Do not infer support from one manual run, do not edit generated evidence, do not
add this charter or its observations to the validator, and do not extend the
declared engine of the register from one manual reading. Report the observation
alongside the deterministic target smoke result so that it can be reproduced and
turned into an automated test when the browser permits it.

## References

- [Capability register of this adapter](../../../../capabilities/web.md) — §2 bottom rows, §3.7-3.8
- [Web browser driver](../README.md) — Phase 4 limits and published availability
- [Web Phase 1 lifecycle charter](phase-1-lifecycle.md) — the static host and the target commands
- [Web Phase 3 input charter](phase-3-input.md) — procedure 6, the pointer-lock deferral this charter discharges
- [Kadre design](../../../../DESIGN.md) — §9.6, §15.3
- [Operation contracts](../../../../OPERATION-CONTRACTS.md) — §1.1, §4
- [Web implementation roadmap](../../../../WEB-IMPLEMENTATION-ROADMAP.md)
