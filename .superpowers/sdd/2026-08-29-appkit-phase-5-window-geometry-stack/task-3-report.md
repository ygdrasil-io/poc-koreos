# Task 3 — Private AppKit window geometry routing

## Result

Implemented the private AppKit geometry seam and deterministic driver path for
`contentSize`, `minimumSize`, `maximumSize`, and `resizable`. Public AppKit
capabilities and factory defaults were not enabled or changed. Native handles
remain confined to `AppKitNativeWindowPort` and its future KFFI implementation.

## TDD evidence

### RED

Added the three behavior tests requested in
`AppKitWindowRuntimeDriverTest` before production implementation:

- `peerForwardsGeometryUpdatesAndReturnsTheEffectiveNativeSnapshot`
- `peerSuppressesManagedResizeCallbacksButForwardsExternalGeometry`
- `peerPreservesUnrelatedStyleMaskBitsWhenChangingResizable`

Ran:

```text
./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --console=plain
```

The run failed at `:kadre:backend:appkit:compileKotlinJvm` because
`AppKitWindowCommandPort` did not implement the newly required
`WindowCommandPort.requestUpdate` method. This was the expected missing driver
route, before any geometry production code was added.

### GREEN

Added the minimal private native target/snapshot and observer seam, peer-local
managed-callback guard, command queue routing/cancellation tracking, and
uncorrelated runtime observation ingress. The deterministic port records real
geometry targets, returns a separately configured effective snapshot, emits
synchronous managed callbacks, and emits external callbacks.

The requested focused suite passed, followed by the complete AppKit and runtime
JVM suites:

```text
./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --console=plain
./gradlew :kadre:backend:appkit:jvmTest --console=plain
./gradlew :kadre:runtime:jvmTest --console=plain
```

## Files changed

- `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
  - private, native-address-free geometry target/snapshot and observer contract.
- `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
  - serialized peer mutation and scoped managed-resize suppression.
- `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
  - async command admission, pre-commit cancellation, correlated completion,
    and serialized external geometry stimuli.
- `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
  - narrow native-observation ingress required to publish an uncorrelated
    geometry observation without leaking a peer or native handle; runtime now
    assigns the correlated revision as its own authority.
- `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`
  - requested behavior-first driver/peer coverage and deterministic native seam.

## Self-review

- The peer suppresses callbacks only while its own `updateGeometry` call is on
  the native owner thread. A callback arriving after that call is emitted as an
  uncorrelated stimulus.
- The command queue removes a command only before `nativeCommitStarted`; once
  a native call starts, cancellation reports too late and completion remains
  observable through the portable runtime fences.
- Geometry stimuli are buffered until runtime commit and then delivered through
  the command serializer. They never take the close branch.
- The style-mask test starts with unrelated bits and proves that changing
  `resizable` clears only the resizable bit in the deterministic native seam.
- `git diff --check` passed. No KFFI/Kextract code, manual FFI, public
  capability activation, or factory-default behavior was changed.

## Concern / follow-up

`KffiAppKitWindowPort` deliberately retains the default unsupported geometry
implementation in this PR. Task 4 must override `updateGeometry` and
`observeGeometry` using generated KFFI setters/getters, capture/restore native
constraint defaults for `Clear`, and preserve the actual `NSWindow` style-mask
bits. This PR supplies the private seam and deterministic proof path only.

## Review fix — command completion and reentrant geometry

### Root cause

The command queue marked a geometry operation as dispatch-started in the
portable runtime before the queued native task ran. If `closeAdmitted` or the
driver `closed` flag became true first, `applyGeometry` returned without an
`applied` or `rejected` stimulus. The runtime therefore retained its dispatched
operation indefinitely.

The callback gate also used only a mutation-depth counter. It dropped every
geometry callback delivered synchronously by a native setter, including a
distinct external/reentrant observation.

### RED

Added two deterministic regression tests before changing production code:

- `queuedGeometryUpdateCompletesWhenCloseIsAdmittedBeforeNativeCommit`
- `peerForwardsDistinctReentrantGeometryDuringItsManagedMutation`

The focused driver suite failed with a `TimeoutCancellationException` in both
tests. The first timeout proved the silent queued-command drop; the second
proved that the distinct synchronous observation was discarded.

### GREEN

- `applyGeometry` now distinguishes a requester-cancelled queued operation
  from an operation made ineligible by close/driver shutdown. The former stays
  withdrawn; the latter emits an explicit rejection before returning.
- The peer keeps queued geometry snapshots while its native setter executes.
  After the effective snapshot is known, it discards equal managed callbacks
  and publishes every distinct snapshot as an uncorrelated stimulus.
- Geometry observers now release in strict inverse-creation order: input,
  surface, geometry, in both normal close and failed preparation cleanup.

Verification:

```text
./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --console=plain
./gradlew :kadre:backend:appkit:jvmTest :kadre:runtime:jvmTest --console=plain
```

Both commands passed. The remaining concern is unchanged: KFFI installation
and native default/real-style-mask handling are deferred to Task 4.
