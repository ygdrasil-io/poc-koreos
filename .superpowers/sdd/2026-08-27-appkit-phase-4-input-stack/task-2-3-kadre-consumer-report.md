# Tasks 2–3 — Kadre consumer proof report

## Status

**Task 2 (`KFFI-OBJC-004`): delivered.** Kadre consumes the newly published no-argument managed Objective-C BOOL contract in a real AppKit macOS test.

**Task 3 (`KFFI-OBJC-005`): still blocked.** The resolved publication has no typed owner-safe scroll-event posting façade, so this report deliberately adds no scroll consumer test, local FFI wrapper, raw native ownership path, or getter-only pseudo-proof.

## Published dependency refresh

Kadre refreshed its classpath with:

```text
./gradlew :kadre:backend:appkit:dependencies \
  --configuration jvmTestRuntimeClasspath --refresh-dependencies
```

The command completed with `BUILD SUCCESSFUL`. Central Portal snapshot metadata identifies the resolved publication as:

```text
repository: https://central.sonatype.com/repository/maven-snapshots/
module:     org.graphiks:kffi-objc-jvm
version:    1.0.0-20260827.203200-17
lastUpdated: 20260827203200
artifact SHA-1: c2a173bf68267d0224857077379319206f38367a
```

The binary on Kadre's refreshed Gradle classpath exposes:

```text
ObjCMethodSignatures.getBoolean()
ObjCMethodRouter.onBoolean(String, boolean, Function0<Boolean>)
```

This is checked with `javap` on the resolved JAR, rather than inferred from a local KFFI checkout.

## Kadre change

Modified:

```text
kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt
```

Added the focused macOS test:

```text
publishedKffiManagedNsViewAnswersAcceptsFirstResponderThroughBooleanSignatureOnMacOs
```

It registers a managed subclass whose real superclass is `NSView`, declares:

```kotlin
"acceptsFirstResponder" to ObjCMethodSignatures.Boolean
```

and binds the published no-argument API:

```kotlin
onBoolean("acceptsFirstResponder", fallback = false) { true }
```

The assertion invokes the public generated `NSView.acceptsFirstResponder()` binding on the managed instance and verifies both its `true` result and one handler invocation. This exercises the Objective-C `BOOL(id, SEL)` callback crossing; it does not inspect a symbol or emulate the callback in Kadre.

No Kadre production code was added or changed.

## RED/GREEN evidence

TDD for production code is not applicable because the deliverable is exclusively a consumer proof and requires no Kadre production implementation. A RED test against the newly published dependency would be artificial: the intended external API is already present, so the consumer test is expected to compile and pass as soon as it is written. Intentionally changing its import or selector merely to create a failure would not validate Kadre behavior.

The meaningful consumer GREEN evidence is the targeted compile-and-native execution below. Before the refresh, the previous snapshot lacked `Boolean` and `onBoolean`; the current test’s compilation against build 17 therefore also proves the resolution transition.

## Verification

```text
./gradlew :kadre:backend:appkit:jvmTest \
  --tests org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.publishedKffiManagedNsViewAnswersAcceptsFirstResponderThroughBooleanSignatureOnMacOs \
  --refresh-dependencies
```

Result: `BUILD SUCCESSFUL`.

The JUnit XML records exactly this test case as executed on the JVM, with a duration of `0.127s`:

```text
publishedKffiManagedNsViewAnswersAcceptsFirstResponderThroughBooleanSignatureOnMacOs[jvm]
```

`git diff --check` was also run for the final staged content.

## Task 3 reservation

Build 17 exposes Task 2's managed BOOL API but still has no `CGScrollWheelEvent` or `AppKitScrollWheelEvent` public class in the resolved JAR. The typed scroll injection proof remains pending the publication of KFFI commit:

```text
3759d86d8054d043de3233eec8c6c0cfe837611f
feat(shared): post typed AppKit scroll wheel events
```

Until that public façade is in the snapshot, Kadre must not bypass it via `CGEventCreateScrollWheelEvent2`, a `MemorySegment`, or custom ownership/release code.

## Files touched and auto-review

- Modified: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Added: this SDD report.

The pre-existing untracked paths were preserved and will not be staged:

```text
.superpowers/plans/
.superpowers/specs/
kadre/implementation-plans/2026-08-25-kffi-objc-foundations.md
```

Auto-review confirms the change is scoped to a Kadre test plus its SDD artefact; it has no KFFI/Kextract modification, no production Kadre change, no scroll code, no local FFI wrapper, no raw native type in the consumer test, no push, and no PR.
