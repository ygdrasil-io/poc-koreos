# Task 5 — synchronous interaction runtime

## Repair round 3

- Parent commit: `e2b44a9` — `fix(runtime): reject closed interaction requests`.
- This follow-up keeps the interaction outcome registration live when a disposed
  collector is found in a publication snapshot, and retains terminal overflow
  failures for every current and future outcome collector.
- The runtime-only pointer path remains payload-only: it carries pressure into
  exactly one ordinary pointer input after the synchronous handler.

## RED → GREEN

- RED (previous repair):
  `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeInteractionHandlerTest.closeBeforeRequestRejectsTheNativeAction --console=plain`
  failed because the native action was admitted after `registration.close()`.
- RED (this repair):
  `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeInteractionHandlerTest --console=plain`
  failed in the new CloseSource and FailSession overflow scenarios: a future
  `outcomes` collector completed normally instead of failing with
  `KadreException(SourceOverflow(Interaction))`.
- RED (stale-snapshot seam):
  `rtk ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeInteractionHandlerTest.collectorDisposedAfterTheOutcomeSnapshotCannotOverflowTheRegistration --console=plain`
  did not compile until the deterministic post-snapshot test seam existed.
- GREEN: the focused stale-snapshot command and the complete
  `RuntimeInteractionHandlerTest` suite pass. Subscriber disposal atomically
  deactivates delivery before the channel is closed, so a stale snapshot is a
  no-op rather than an overflow. Actual CloseSource/FailSession overflow keeps
  the terminal failure, drains admitted outcomes, clears the owner slot, and
  invokes FailSession exactly once.

## Behavioral coverage

- Competing callback threads preserve handler, outcome, and ordinary-input
  stamp order.
- Detach returns while an admitted handler later calls `requestRedraw`; no
  later handler is admitted.
- A close issued from the handler after an accepted action drains the committed
  outcome and then completes the outcome flow.
- Retained context on another active surface returns `WrongSurface`.
- Empty handler capabilities are normalised to unsupported.
- Runtime pointer pressure is projected into one ordinary pointer input.

## Gates

- `rtk ./gradlew :kadre:runtime:jvmTest --console=plain` — succès.
- `rtk ./gradlew :kadre:foundation:jvmTest :kadre:runtime:jvmTest :kadre:foundation:checkKotlinAbi --console=plain` — succès.
- `rtk git diff --check` — succès avant commit.
