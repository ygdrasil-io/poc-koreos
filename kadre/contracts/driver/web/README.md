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
public `HTMLElement.attachKadre` API and produce the browser evidence consumed
by the contract gates. The tests cover deterministic lifecycle behaviour:
ownership, detach/reinsert, cross-document transfer, Shadow DOM observation,
`Manual` reconnection, focus and visibility reduction, `pagehide`, and the
absence of Kadre-created DOM or a primary window.

The phase-1 tests use a pinned Playwright/Chromium installation. A local
browser installation is prepared by the Gradle tasks; no system browser version
is an input to the automated smoke.

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
