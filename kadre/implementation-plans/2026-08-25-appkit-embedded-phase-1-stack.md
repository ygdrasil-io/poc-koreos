# AppKit Embedded Phase 1 — Stacked PR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** make the AppKit desktop backend embeddable in an existing `NSApplication` loop while preserving standalone ownership, session isolation, and explicit lifecycle semantics.

**Architecture:** the delivery is a three-PR stack. The first PR consumes the published typed KFFI surface and closes Kadre's KFFI dependency record. The second introduces a process-wide broker with a deterministic, SDK-free lifecycle boundary. The third connects that broker to the KFFI-managed `NSNotificationCenter` observers, enables `Embedded(AppKitMainLoop)`, and activates `APK-002` only with executable proof.

**Tech Stack:** Kotlin 2.4, kotlinx.coroutines, JDK 25 FFM through `org.graphiks:kffi-objc:1.0.0-SNAPSHOT`, AppKit, Gradle, and the Kadre contract registry.

**Spec:** [`../APPKIT-IMPLEMENTATION-ROADMAP.md`](../APPKIT-IMPLEMENTATION-ROADMAP.md), [`../APPKIT-JVM-FIRST-IMPLEMENTATION.md`](../APPKIT-JVM-FIRST-IMPLEMENTATION.md), [`../KFFI-REQUIREMENTS.md`](../KFFI-REQUIREMENTS.md), and [`../TEST-STRATEGY.md`](../TEST-STRATEGY.md).

## Global constraints

- Keep all FFI, Objective-C callback and ownership primitives in KFFI; Kadre calls only KFFI's generated or managed public API.
- `Embedded(AppKitMainLoop)` requires the JVM main thread and an AppKit loop already running. It never calls `run`, `stop`, `terminate:`, changes the delegate, or changes the activation policy.
- A Kadre embedded session owns only its `RuntimeHostController`, coroutine hierarchy, and its broker registration. It cannot affect another session or the native loop.
- Native termination is the only process-wide terminal event. It produces `HostDetached` for every still-attached session.
- Tests assert observable ownership, lifecycle and termination outcomes. Do not add source-text or mock-call-count tests.
- The existing untracked `implementation-plans/2026-08-25-kffi-objc-foundations.md` is user-owned and is not part of this stack.

## Stack topology

```text
origin/master
  └── PR 1  codex/appkit-embedded-foundation
        └── PR 2  codex/appkit-embedded-broker
              └── PR 3  codex/appkit-embedded-lifecycle
```

Each pull request is opened as a draft against its immediate predecessor. Review and merge order is therefore unambiguous; after a predecessor merges, retarget the next PR manually to `master` and rebase it on the merged result before merging it.

---

### Task 1 — PR 1: consume typed KFFI and close the foundation record

**Branch:** `codex/appkit-embedded-foundation` from `origin/master`.

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeApplication.kt`
- Modify: `kadre/KFFI-REQUIREMENTS.md`
- Modify: `kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Test: existing `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`

**Consumes:** `NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2`, `NSEventType.NSEventTypeApplicationDefined`, `NSEventModifierFlags`, and `NSPoint` from the published `kffi-objc` snapshot.

**Produces:** a backend that compiles exclusively against public typed KFFI APIs; a documentation record that closes `KFFI-OBJC-001`, `KFFI-OBJC-002`, and `KFFI-OBJC-003` with the merged KFFI/Kextract references.

- [ ] **Step 1: Run the focused AppKit test to observe the incompatible ABI use.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.realKffiStandaloneLoopStartsAndStopsOnMacOs
  ```

  Expected: Kotlin compilation fails because `AppKitNativeApplication.kt` accesses KFFI's now-internal `ObjCRuntime.ObjCStructArg`.

- [ ] **Step 2: Replace the generic Objective-C message with the typed KFFI factory.**

  Allocate an `NSPoint` in a confined `Arena`, write `(0.0, 0.0)`, then invoke the generated `NSEvent` factory:

  ```kotlin
  private fun createWakeEvent(): MemorySegment = Arena.ofConfined().use { arena ->
      val origin = NSPoint.allocate(arena).also { point ->
          point.x = 0.0
          point.y = 0.0
      }
      NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2(
          NSEventType.NSEventTypeApplicationDefined,
          origin,
          NSEventModifierFlags(0L),
          0.0,
          0L,
          MemorySegment.NULL,
          0,
          0L,
          0L,
      )
  }
  ```

  Remove `GroupLayout`, `MemoryLayout`, `ValueLayout`, the raw event constants, and the direct generic `ObjCRuntime.msgSend` call from Kadre.

- [ ] **Step 3: Update the dependency and phase documents.**

  Mark the three KFFI requirements closed, cite KFFI PR `Graphiks-org/kffi#35`, Kextract PR `klang-toolkit/kextract#50`, and the published `1.0.0-SNAPSHOT`. Replace the obsolete standalone workaround text with the typed `NSEvent` call. Mark Phase 0's gate satisfied and remove the statement that embedded is deferred on `KFFI-OBJC-001/003`.

- [ ] **Step 4: Verify the focused native regression and full Kadre gate.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.realKffiStandaloneLoopStartsAndStopsOnMacOs
  ./gradlew :kadre:check
  ```

  Expected: the real macOS loop starts and stops twice, and the complete Kadre gate compiles and passes without access to KFFI internals.

- [ ] **Step 5: Commit the independently reviewable migration.**

  ```bash
  git add kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeApplication.kt \
    kadre/KFFI-REQUIREMENTS.md kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md \
    kadre/APPKIT-IMPLEMENTATION-ROADMAP.md \
    kadre/implementation-plans/2026-08-25-appkit-embedded-phase-1-stack.md
  git commit -m "fix(appkit): consume typed KFFI wake event"
  ```

---

### Task 2 — PR 2: broker ownership and deterministic lifecycle routing

**Branch:** `codex/appkit-embedded-broker` from PR 1.

**Files:**

- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitProcessBroker.kt`
- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitLifecycleSignal.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitStandaloneOwnership.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Create: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitProcessBrokerTest.kt`

**Consumes:** `RuntimeHostController.updateLifecycle`, `RuntimeHostController.detach`, and the current standalone stop protocol.

**Produces:** `AppKitProcessBroker` as the only owner of process-wide host registrations. It exposes idempotent registration leases and translates an SDK-independent `AppKitLifecycleSignal` to the exact `LifecycleState` transitions required by the runtime.

- [ ] **Step 1: Write failing broker tests with real `RuntimeHostController` instances.**

  Cover the observable mutations:

  ```kotlin
  val first = broker.registerEmbedded(firstHost)
  val second = broker.registerEmbedded(secondHost)
  broker.accept(AppKitLifecycleSignal.BecameInactive)
  assertEquals(ActivationState.Inactive, firstSession.lifecycle.state.value.activation)
  assertEquals(ActivationState.Inactive, secondSession.lifecycle.state.value.activation)
  first.close()
  broker.accept(AppKitLifecycleSignal.HostTerminated)
  assertEquals(SessionStopReason.ApplicationRequested, firstOutcome)
  assertEquals(SessionStopReason.HostDetached, secondOutcome)
  ```

  Include duplicate-close, sequential reuse, and a test proving a standalone lease is exclusive while embedded registrations remain individually closeable.

- [ ] **Step 2: Run the broker test before implementation.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitProcessBrokerTest
  ```

  Expected: compilation fails because `AppKitProcessBroker` and `AppKitLifecycleSignal` do not exist.

- [ ] **Step 3: Implement broker state and migrate standalone ownership.**

  Define the finite input set:

  ```kotlin
  internal enum class AppKitLifecycleSignal {
      BecameActive, BecameInactive, DidHide, DidUnhide, HostTerminated,
  }
  ```

  `AppKitProcessBroker` holds registrations behind one lock. A registration removes exactly its own `RuntimeHostController`; `HostTerminated` snapshots then clears all registrations and calls `detach()` outside the lock. `BecameActive`, `BecameInactive`, `DidHide`, and `DidUnhide` preserve the other axes of the most recent lifecycle state. Reimplement the existing standalone lease as a broker lease so a later native adapter cannot bypass process ownership.

- [ ] **Step 4: Verify broker and existing standalone contracts.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest
  ./gradlew :kadre:check
  ```

  Expected: process registration does not create any native callback; standalone behavior remains unchanged and all broker interleavings are deterministic.

- [ ] **Step 5: Commit the broker boundary.**

  ```bash
  git add kadre/backend/appkit/src
  git commit -m "feat(appkit): add process broker lifecycle routing"
  ```

---

### Task 3 — PR 3: embedded attach and AppKit lifecycle observations

**Branch:** `codex/appkit-embedded-lifecycle` from PR 2.

**Files:**

- Create: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitLifecycleSource.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeApplication.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`
- Modify: `kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`

**Consumes:** `AppKitProcessBroker`, `NSNotificationCenter.observe`, and AppKit notification constants exposed by KFFI.

**Produces:** a supported `DesktopIntegrationKind.AppKitMainLoop`, attached sessions with their own closeable lifecycle observation owner, and an active `APK-002` contract with O2 deterministic proof plus O3 macOS notification proof.

- [ ] **Step 1: Write failing provider tests for embedded semantics.**

  Exercise the provider, not private routing helpers. Use a deterministic fake lifecycle source to prove:

  ```kotlin
  val first = provider.attach(embeddedRequest(firstScope, firstFactory))
  val second = provider.attach(embeddedRequest(secondScope, secondFactory))
  lifecycle.emit(AppKitLifecycleSignal.DidHide)
  assertEquals(VisibilityState.Background, first.value.lifecycle.state.value.visibility)
  first.value.close()
  assertTrue(second.value.lifecycle.state.value.attachment == AttachmentState.Attached)
  lifecycle.emit(AppKitLifecycleSignal.HostTerminated)
  assertEquals(SessionStopReason.HostDetached, second.value.awaitTermination().reason)
  ```

  Include rejection before factory creation when the current thread is not the main thread, the native loop is not already running, or `integration != AppKitMainLoop`.

- [ ] **Step 2: Run the focused provider tests before implementation.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest \
    --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest
  ```

  Expected: embedded assertions fail because AppKit is absent from `supportedIntegrations` and `attach` returns `Unsupported(HostAttach)`.

- [ ] **Step 3: Implement attach without loop ownership.**

  Expand `AppKitNativeApplication` with `isMainThread()` and `isRunning()` checks already used by standalone plus a `startLifecycleObservation(listener)` method returning `AutoCloseable`. The KFFI implementation obtains `NSApplication.sharedApplication()` and `NSNotificationCenter.defaultCenter()`, installs selector-based observations for active, resign-active, hide, unhide and will-terminate, and closes all observations idempotently. It performs no raw FFM operation and does not retain a borrowed notification.

  `AppKitBackendProvider.attach` accepts only `AppKitMainLoop`, validates availability, main thread and native loop state before invoking the factory, then registers the created `RuntimeHostController` with the broker. The session observer closes only its own broker lease. A session stop in embedded mode returns no native stop action. The activation policy remains untouched.

- [ ] **Step 4: Add the native O3 lifecycle proof.**

  On macOS, create the real KFFI lifecycle source, attach a session from the main thread while the AppKit loop is pumping, post `NSApplicationDidHideNotification`, and observe the public lifecycle state. Then close the session, post a second notification, and prove no session state is mutated. Keep the test scoped to the existing AppKit CI job and do not synthesize success when a native runner is unavailable.

- [ ] **Step 5: Activate the contract and map evidence.**

  Change `APK-002` from `planned` to `active`; name positive, isolation, host-termination and refusal scenarios; map every scenario to its focused test in `evidence.tsv`. Update the roadmap and first-implementation document from `planned` to implemented only for Phase 1, leaving windows and input explicitly deferred.

- [ ] **Step 6: Verify the complete stack.**

  Run:

  ```bash
  ./gradlew :kadre:backend:appkit:jvmTest
  ./gradlew :kadre:contracts:validator:check
  ./gradlew :kadre:check
  ```

  Expected: embedded and standalone paths remain green, evidence validates, and no native callback can outlive its session observation owner.

- [ ] **Step 7: Commit the user-visible embedded feature.**

  ```bash
  git add kadre/backend/appkit/src kadre/contracts/registry/contracts.tsv \
    kadre/backend/appkit/contracts/evidence.tsv kadre/APPKIT-IMPLEMENTATION-ROADMAP.md \
    kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md
  git commit -m "feat(appkit): attach to embedded AppKit loops"
  ```

## Final verification and PR creation

- [ ] Rebase each branch linearly on its immediate base without modifying the user-owned KFFI foundations plan.
- [ ] Run `./gradlew :kadre:check` on each branch tip.
- [ ] Push the three branches and create draft PRs with bases `master`, `codex/appkit-embedded-foundation`, and `codex/appkit-embedded-broker` respectively.
- [ ] State in every PR description that it is one layer of the AppKit embedded Phase 1 stack and link its predecessor/successor.
