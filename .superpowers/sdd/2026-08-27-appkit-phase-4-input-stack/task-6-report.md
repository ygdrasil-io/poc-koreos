# Task 6 report — public AppKit input

## Commit

- `ea61181b3488e863141791b0e1b0e45f24b6b9cc feat(appkit): activate public input contract`

## Dependency

- Refreshed Gradle resolution: `org.graphiks:kffi-objc-jvm:1.0.0-SNAPSHOT:20260829.040320-20`.
- Kadre consumes `NSApplication.postScrollWheelEvent(AppKitScrollWheelEvent)` and CoreGraphics scroll enums only; no branch dependency or new raw FFI was added.

## TDD record

- RED: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.phase4InputHarnessWritesAnHonestNoninteractiveRecordOnMacOs --rerun-tasks --console=plain`
- Expected failure: `ClassNotFoundException: org.graphiks.kadre.internal.appkit.manual.Phase4InputHarnessKt`.
- GREEN: the same command passed after adding the harness. The scroll proof first exposed the published constructor spelling `isContinuous` (not `continuous`); its focused test then passed.

## Final verification

- `:kadre:backend:appkit:jvmTest` focused harness and focused KFFI scroll proof: passed.
- `:kadre:contracts:validator:validateContractRegistry --rerun-tasks`: passed.
- `:kadre:backend:appkit:appKitNativeTests :kadre:contracts:validator:generateAppKitContractEvidence -PkadreContractCommit=local --rerun-tasks --no-daemon`: passed; generated `APK-005.json`.
- `:kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:check --rerun-tasks --no-daemon`: passed (113 tasks).
- `git diff --check`: passed before commit.

## Affected files

- Public O3/harness test, Phase 4 harness and manual guide.
- `APK-005` registry/evidence, validator task list, macOS script artifact gate.
- `KFFI-REQUIREMENTS.md`: `KFFI-OBJC-004` and `KFFI-OBJC-005` closed.

## Concern

A script rerun once failed in existing KFFI-native tests with transient worker `NoClassDefFoundError` / `ClassNotFoundException`, including an absent `RuntimeSessionComponentsSpiTest`; the smallest equivalent `:kadre:backend:appkit:jvmTest --rerun-tasks --no-daemon` immediately passed without source/config changes. This is recorded as an unreproduced AppKit worker/fork flake; no retry workaround or scope expansion was added. The manual guide explicitly reserves real responder scroll, fractional deltas and momentum for human verification because the typed synthetic event has `windowNumber == 0`.

## Corrective loop after public review

- The phase-4 harness now records every capability. `textInput` and `rawInput` use a canonical
  `Unsupported(operation=...)` encoding, and the process-level proof rejects `M7 pass` until it
  has observed terminal detachment.
- The native first-responder proof now checks `NSWindow.firstResponder` against the managed
  `NSView`, rather than only calling `acceptsFirstResponder` and the callback directly.
- The actual process-owning AppKit loop now proves public input state-before-event, focus reset
  without a synthetic key release, and two-window input isolation. The corresponding `APK-005`
  sentinels point to this O3 test rather than to deterministic driver tests.
- KFFI #43 is now published as `1.0.0-SNAPSHOT:20260829.065753-21`. A fresh resolution reaches
  the real queue; the consumer proof filters `NSEventMaskScrollWheel`, dequeues exactly two FIFO
  events and no third, and checks type, target-less `windowNumber`, precision, deltas, phase and
  momentum. The preceding snapshot's `NSDefaultRunLoopMode` `WrongMethodTypeException` is gone.
- The native task emits an unrelated physical `PointerMoved` before the synthetic key in some runs.
  The O3 proof deliberately waits for the injected `InputEvent.Key`, then retains its state-before-
  event assertion; it does not deny that real pointer input can coexist with the proof.
- Final verification passed with `scripts/test-kadre-appkit-contracts.sh --refresh-dependencies
  --rerun-tasks --no-daemon` and `:kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest
  :kadre:check --refresh-dependencies --rerun-tasks --no-daemon` (113 Gradle tasks). The final
  independent review remains required before commit.
