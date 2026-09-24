# Web browser contract driver

This non-published project runs the public Web host-attachment proof in real
Chromium for both Kotlin/JS IR and Kotlin/Wasm-JS. It is a browser driver, not
an implementation API and not a renderer.

## Automated lifecycle smoke

Run both targets from the repository root:

```shell
rtk ./gradlew :kadre:contracts:driver:web:jsBrowserSmoke :kadre:contracts:driver:web:wasmJsBrowserSmoke --rerun-tasks
```

The target-specific Playwright suites are
[web-lifecycle.spec.mjs](playwright/web-lifecycle.spec.mjs),
[web-surface.spec.mjs](playwright/web-surface.spec.mjs) and
[web-typescript.spec.mjs](playwright/web-typescript.spec.mjs). They exercise the
public `HTMLElement.attachKadre` API and emit target-specific JUnit results for
the lifecycle scenarios that will later belong to a contract proof. Playwright
diagnostics are removed after a successful smoke; they are preserved on a
failure or interruption. The tests cover deterministic lifecycle behaviour:
ownership, detach/reinsert, cross-document transfer, Shadow DOM observation,
`Manual` reconnection, focus and visibility reduction, `pagehide`, the absence
of Kadre-created DOM or a primary window, the surface size/scale readback and
its per-frame redraw coalescing, the element escape hatch, and the TypeScript
consumer.

Each Playwright test title is exactly the contract scenario ID it proves, so a
JUnit `testcase/@name` maps to a scenario without interpretation.

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
`index.mjs`.

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

[phase-1-lifecycle.md](manual/phase-1-lifecycle.md) supplements the automated
suite with browser behaviour that headless automation cannot claim reliably,
including a real back-forward cache traversal, browser-level focus, and host
owned open/closed Shadow DOM. It does not replace the JS and Wasm automated
smokes, does not create validator evidence, and does not change contract
status.

The lifecycle contract and the delivery roadmap remain authoritative:

- [Web implementation roadmap](../../../WEB-IMPLEMENTATION-ROADMAP.md)
- [Kadre design](../../../DESIGN.md)
- [Operation contracts](../../../OPERATION-CONTRACTS.md)
