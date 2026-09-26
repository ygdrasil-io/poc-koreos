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
- [web-typescript.spec.mjs](playwright/web-typescript.spec.mjs) proves the
  published `@kadre/host` facade, driven by the same TypeScript consumer that
  `kadre/consumers/typescript` type-checks.

Playwright diagnostics are removed after a successful smoke; they are preserved
on a failure or interruption. Every identity of
[contracts/evidence.tsv](contracts/evidence.tsv) names what it maps: the four
surface scenarios, the two lease scenarios, the twelve input scenarios and the
consumer test are titled with the evidence id they carry, so a JUnit
`testcase/@name` maps to them without interpretation, while each sentinel is
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
| every surface field at the terminal transition | unavailable, and the input lane is closed rather than reset | `web-input-terminal-closed` |

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
  held.

They are informative, they do not create validator evidence, and they do not
change contract status.

The lifecycle contract and the delivery roadmap remain authoritative:

- [Web implementation roadmap](../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre design](../../../DESIGN.md)
- [Operation contracts](../../../OPERATION-CONTRACTS.md)
