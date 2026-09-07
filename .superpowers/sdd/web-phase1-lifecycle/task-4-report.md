# Task 4 — DOM-independent Web ownership and lifecycle core

## Scope delivered

- Added a DOM-free stable-identity registry with idempotent reservation release and the existing
  `KadreFailure.AlreadyInUse(KadreResourceKind.Host)` representation of the contractual
  `Busy(Host)` outcome.
- Added immutable browser lifecycle snapshots and a reducer for `StopWhenDetached`, `Manual`,
  deterministic visibility/focus reduction, inter-document transfer, and terminal `pagehide`.
- Routed reductions into `RuntimeHostController.updateLifecycle` and `detach`, preserving the
  runtime's `HostDetached` termination path.  Session teardown now releases the target port and
  registry reservation exactly once.
- Added DOM-free common tests for reservation/release, policy admission, detach, transfer,
  manual disconnection, focus/visibility, `pagehide`, idempotence, and runtime termination.

## TDD evidence

RED command (before production files existed):

```text
./gradlew :kadre:platform:web:jsBrowserTest --tests org.graphiks.kadre.platform.web.WebLifecycleReducerTest --tests org.graphiks.kadre.platform.web.WebHostSessionTest
```

It failed in `:kadre:platform:web:compileTestKotlinJs` with the expected unresolved references
to `WebHostRegistry`, `WebHostReservation`, `WebLifecycleSnapshot`, `WebLifecycleReducer`, and
the new `WebHostSession` seam.

## Verification

```text
./gradlew :kadre:platform:web:jsBrowserTest --tests org.graphiks.kadre.platform.web.WebLifecycleReducerTest --tests org.graphiks.kadre.platform.web.WebHostSessionTest
./gradlew :kadre:platform:web:wasmJsBrowserTest --tests org.graphiks.kadre.platform.web.WebLifecycleReducerTest --tests org.graphiks.kadre.platform.web.WebHostSessionTest
./gradlew :kadre:platform:web:jsBrowserTest :kadre:platform:web:wasmJsBrowserTest
git diff --check
```

All commands completed successfully.  The generated `kotlin-js-store/` directory was removed
before committing; no `kadre/contracts/driver/web/node_modules/` directory was created by this
task.

## Commit

`feat(web): add lifecycle ownership core` (this commit).

## Concerns

None.  Target-specific JS/Wasm ports intentionally retain DOM observation, stable element
identity override, observer installation, and Manual rAF reconnection for Task 5.
