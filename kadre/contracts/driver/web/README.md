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
[web-lifecycle.spec.mjs](playwright/web-lifecycle.spec.mjs). They exercise the
public `HTMLElement.attachKadre` API and emit target-specific JUnit and
diagnostic artifacts for the lifecycle scenarios that will later belong to a
contract proof. The tests cover deterministic lifecycle behaviour: ownership,
detach/reinsert, cross-document transfer, Shadow DOM observation, `Manual`
reconnection, focus and visibility reduction, `pagehide`, and the absence of
Kadre-created DOM or a primary window.

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
