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
