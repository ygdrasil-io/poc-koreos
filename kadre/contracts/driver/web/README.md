# Web browser contract driver

This non-published project runs the public Web host-attachment proof in real
Chromium for both Kotlin/JS IR and Kotlin/Wasm-JS. It is a browser driver, not
an implementation API and not a renderer.

## Automated browser smoke

Run both targets from the repository root:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks
```

Four target-specific Playwright suites run in Chromium and emit target-specific
JUnit results:

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
  capture taken for an owned pointer, and the touch boundary of this phase;
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
- [web-typescript.spec.mjs](playwright/web-typescript.spec.mjs) proves the
  published `@kadre/host` facade, driven by the same TypeScript consumer that
  `kadre/consumers/typescript` type-checks.

Playwright diagnostics are removed after a successful smoke; they are preserved
on a failure or interruption. Every identity of
[contracts/evidence.tsv](contracts/evidence.tsv) names what it maps: the four
surface scenarios, the two lease scenarios, the twelve input scenarios, the
seven provider scenarios of `BCK-001` and the five facade scenarios of
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
- touch and gestures stay `Unsupported` until the phase that installs their
  observers (D12): a real tap on a touch-enabled page publishes no pointer, no
  touch and no event, and `InputCapabilities.touch` keeps saying so.
  `web-input-touch-deferred` is the only test that creates its own browsing
  context, because a touch input requires one that declares touch;
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
| `InputCapabilities.touch`, `InputCapabilities.gestures` | `Unsupported` and `Unsupported(GestureInput)`: no touch observation is delivered | `web-input-touch-deferred` |
| `SurfaceCapabilities.pointerCapture` | `Supported({None, Confined}, Available)`; a `Confined` request without an owned pointer is refused `InteractionRequired(Missing)`, and `Locked` is `Unsupported(UpdateSurface)` | `web-input-pointer-capture` |
| `SurfaceCapabilities.inputDefaultBehavior` | `Supported({HostDefault, SuppressWhenPossible}, Available)`; under `HostDefault` no browser default is dropped, under `SuppressWhenPossible` only the wheel and a press of the nine document-scroll keys are | `web-input-default-behavior` |
| the terminal `SurfaceCapabilities` | unavailable: the shared all-`Unsupported` snapshot (`unsupportedSurfaceCapabilities()`, `SurfaceAdmission.kt:20`) | `WebInputSurfaceTest.theInputDefaultBehaviorCapabilityIsTheWholeEnumAndTheOtherFieldsStayUnsupported` — the scenario `web-input-terminal-closed` reads `data-kadre-input-state`, a `SurfaceInputState`, plus the session and flow closure, and the fixture publishes no `SurfaceCapabilities` attribute at all |
| the input lane at the terminal transition | closed rather than reset: a key held at the close stays readable in the frozen snapshot, and no later stimulus is admitted | `web-input-terminal-closed` |

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
| `SurfaceCapabilities.handlerInteractions` | `Unsupported(InstallInteractionHandler)` before installation and at the terminal snapshot; `Supported({EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer}, Available)` once the session configuration installed, at the same structural moment as `keyboard`/`pointer` | `WebInteractionSurfaceTest.handlerInteractionsIsTheFourWebActionsOnceStructurallyInstalled`, `.thePreInstallSnapshotClaimsNoInteractionAtAll`; the smoke scenario `web-interaction-fullscreen` reads the armed flag `data-kadre-interaction-armed` the fixture sets after a successful install |
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
  headlessly.

They are informative, they do not create validator evidence, and they do not
change contract status.

The lifecycle contract and the delivery roadmap remain authoritative:

- [Web implementation roadmap](../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre design](../../../DESIGN.md)
- [Operation contracts](../../../OPERATION-CONTRACTS.md)
