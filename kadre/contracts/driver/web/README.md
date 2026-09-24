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
package's published `index.mjs` shim, and the page's Kotlin application
publishes the opaque factory key the consumer passes back to `KadreWeb.attach`.

The shim's relative import of its sibling Kotlin module is remapped, by the same
import map, to a small application module that the driver serves at
`/host/kadre-host-application.js`: it exposes the *application's* interop
bindings to the shim and loads the published module first, publishing the binding
names that module declares as `kadre-published-host-bindings` (asserted by the
spec). The remap is necessary because a browser page's Kotlin application cannot
share one interop instance with the published module:

- a Kotlin/Wasm library module statically links its dependencies, so the
  published `kadre-platform-web.wasm` is a second, isolated instance, and a
  factory key registered by the application is unknown to it
  (`KadreHostError: {"kind":"invalidRequest","field":"factoryKey"}`);
- the Kotlin/JS library build publishes its `@JsExport` names nested on its
  module object under the package path, while the published shim reads them from
  the top level of the module-name global, so importing the shipped JS shim
  against the shipped JS module fails on
  `@kadre/host: the Kotlin module did not publish its bindings`. That build also
  dead-code-eliminates `KadreApplicationFactory.asHostRef`, `hostKey` and
  `KadreApplicationFactoryRef`, so no application can obtain a usable key from
  the published JS module at all. Both are recorded in the Task 7 report; a
  follow-up in `kadre/platform/web` owns them.

`--consumer=<directory>` is required: the runner serves that directory at
`/host` and fails before Playwright starts when it is absent or does not carry
`index.mjs`.

`BCK-001` remains `planned` in Phase 1. No active contract gate consumes these
artifacts and this driver does not activate that capability.

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
