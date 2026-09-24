# Web browser contract driver

This non-published project runs the public Web host-attachment proof in real
Chromium for both Kotlin/JS IR and Kotlin/Wasm-JS. It is a browser driver, not
an implementation API and not a renderer.

## Automated browser smoke

Run both targets from the repository root:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks
```

Three target-specific Playwright suites run in Chromium and emit target-specific
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
- [web-typescript.spec.mjs](playwright/web-typescript.spec.mjs) proves the
  published `@kadre/host` facade, driven by the same TypeScript consumer that
  `kadre/consumers/typescript` type-checks.

Playwright diagnostics are removed after a successful smoke; they are preserved
on a failure or interruption. Every Playwright test title is exactly the contract
scenario ID it proves, so a JUnit `testcase/@name` maps to a scenario without
interpretation.

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

The two Gradle tasks `:kadre:contracts:validator:validateJsBrowserContractEvidence`
and `...:validateWasmJsBrowserContractEvidence` read those documents against the
same registry, the same mapping and the same JUnit report; they are part of
`:kadre:contracts:validator:check`.

## Phase 2 limits

These are the real boundaries of the delivered phase, not defects:

- a redraw buffer whose overflow action is `DropOldestAndReport` or
  `DropLatestAndReport` only ever drops the excess request: a surface has no
  diagnostic channel in this phase that could carry the report half;
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
built from, and the Gradle smoke task passes the repository's own pair.

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
