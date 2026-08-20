# Cross-platform correctness closure implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining cross-platform correctness defects and make their CI evidence blocking, deterministic, and traceable.

**Architecture:** Preserve the established non-UIKit startup order while making the callback trace explicit. Use synchronous logical invalidation for thread-confined X11/Wayland native closes, deadline rearming for early timers, and injectable selection/probe seams for Linux routing. CI scripts own pass/fail decisions and workflows only coordinate them.

**Tech Stack:** Kotlin 2.4.0, Kotlin Multiplatform, JVM/FFM on JDK 25, Kotlin/Native iOS simulator, Kotlin/JS and Wasm, Gradle, Bash, Python 3, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-19-cross-platform-correctness-closure-design.md`

## Global Constraints

- Windows and Win32-specific behaviour remain out of scope.
- Retain the five previously approved compatibility changes; document the user-approved sixth change: an empty monitor list after removal of the final known Wayland output.
- No new public API break is permitted.
- The canonical lifecycle table in the closure spec replaces conflicting lifecycle prose in plans 04 through 07.
- Use test-first development for every behavioural change; record a focused failing result before production code changes.
- Deterministic CI must not mask failure with `continue-on-error`, `|| true`, or a successful early return.
- The required PR critical path stays below 30 minutes; each required job timeout is at most 25 minutes.
- Prefix every repository shell command with `rtk`.

---

## File and responsibility map

| Area | Production files | Tests and evidence |
| --- | --- | --- |
| Iteration contract | `kadre-test/.../EventLoopConformance.kt` | `EventLoopConformanceTest.kt`, backend conformance adapters |
| Terminal close and outputs | `X11WindowLifecycle.kt`, `WaylandEventLoop.kt` | `X11LoopContractTest.kt`, `WaylandLoopContractTest.kt` |
| Deadline semantics | `BrowserScheduler.kt`, `X11EventLoop.kt`, `WaylandEventLoop.kt` | `WebEventLoopTest.kt`, X11/Wayland contract tests |
| UIKit lifecycle | `KadreAppDelegate.kt`, `UIKitActiveEventLoop.kt` | `UIKitLifecycleTest.kt` |
| Linux facade | `LinuxBackendDetector.kt`, `EventLoop.jvm.kt` | `LinuxBackendDetectorTest.kt` |
| CI, docs, closure evidence | `.github/workflows/*.yml`, `scripts/*`, `docs/kadre/*`, `CHANGELOG.md` | workflow-contract test, report validator, 19-row closure report |

### Task 1: Make the shared iteration contract executable

**Files:**
- Modify: `kadre-test/src/commonMain/kotlin/org/graphiks/kadre/test/EventLoopConformance.kt:47-116`
- Modify: `kadre-test/src/commonTest/kotlin/org/graphiks/kadre/test/EventLoopConformanceTest.kt:1-103`
- Modify: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11LoopContractTest.kt`
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandLoopContractTest.kt`
- Modify: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/AppKitRegistryLifecycleTest.kt`
- Modify: `kadre-android/src/androidHostTest/kotlin/org/graphiks/kadre/android/AndroidLoopStateTest.kt`
- Modify: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UIKitLifecycleTest.kt`
- Modify: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebEventLoopTest.kt`

**Interfaces:**
- Consumes: `ObservedCallback`, `RecordingApplicationHandler`, and the lifecycle table in the closure spec.
- Produces: one strict `assertIterationOrder(trace)` invariant and one backend test adapter per Android, X11, Wayland, AppKit, UIKit, Web JS, and Web Wasm.

- [ ] **Step 1: Add the failing negative conformance cases**

Add tests that reject a second `NewEvents`, a missing `AboutToWait`, a dispatch before `NewEvents`, and any callback after `AboutToWait`:

```kotlin
@Test
fun callbackAfterAboutToWaitIsRejected() {
    assertFailsWith<AssertionError> {
        assertIterationOrder(
            listOf(NewEvents, AboutToWait, WindowEvent),
        )
    }
}
```

- [ ] **Step 2: Run the focused common test and record the expected failure**

Run: `rtk ./gradlew :kadre-test:jvmTest --tests '*EventLoopConformanceTest' --no-daemon --console=plain`

Expected: the new cases fail because `assertIterationOrder` currently only checks the first dispatch and last `AboutToWait`.

- [ ] **Step 3: Tighten the conformance helper**

Make `assertIterationOrder` require exactly one `NewEvents`, exactly one final `AboutToWait`, no callbacks after it, and dispatch callbacks only inside that interval. Keep lifecycle callbacks outside an iteration only where the canonical table explicitly places them.

```kotlin
val start = trace.indexOf(ObservedCallback.NewEvents)
val end = trace.indexOf(ObservedCallback.AboutToWait)
check(trace.count { it == ObservedCallback.NewEvents } == 1)
check(end == trace.lastIndex)
check(start in 0 until end)
```

- [ ] **Step 4: Add backend-specific positive adapters**

In each listed backend test file, record one complete trace matching the lifecycle table or a normal dispatch iteration and pass it through the shared helper. Web must execute the assertion through both JS and Wasm test targets.

- [ ] **Step 5: Run the common and backend contract gates**

Run: `rtk ./gradlew :kadre-test:allTests :kadre-x11:jvmTest :kadre-wayland:jvmTest :kadre-appkit:jvmTest :kadre-android:testAndroidHostTest :kadre-web-common:allTests --no-daemon --console=plain`

Expected: all selected tests pass; the iOS adapter is verified by Task 4 on the simulator.

- [ ] **Step 6: Commit the contract work**

```bash
rtk git add kadre-test kadre-x11/src/jvmTest kadre-wayland/src/jvmTest kadre-appkit/src/jvmTest kadre-android/src/androidHostTest kadre-uikit/src/iosTest kadre-web-common/src/webTest
rtk git commit -m "test: enforce complete event-loop iterations"
```

### Task 2: Make X11 and Wayland closes terminal and outputs live

**Files:**
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11WindowLifecycle.kt:73-280`
- Modify: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11LoopContractTest.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt:140-360,455-475`
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandLoopContractTest.kt`
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ActiveEventLoop.kt:90-105` if its monitor KDoc guarantees a synthetic monitor

**Interfaces:**
- Consumes: the `OPEN → TOMBSTONED → NATIVE_CLOSED → DESTROYED_DELIVERED` state model.
- Produces: synchronous logical tombstoning, exactly one terminal notification, and distinct Wayland discovery/output-empty monitor states.

- [ ] **Step 1: Write failing terminal-close and final-output tests**

Add one X11 and one Wayland test in which a window event is already in the boundary batch when public `close()` returns; assert the event is suppressed, `Destroyed` is delivered once, and another live window still receives its event. Add a Wayland test that removes the final registered output and asserts `availableMonitors().isEmpty()`.

```kotlin
window.close()
enqueueWindowEvent(window.id, WindowEvent.Focused(true))
drain(handler)
assertEquals(listOf(WindowEvent.Destroyed), deliveredFor(window.id))
assertEquals(listOf(WindowEvent.Focused(true)), deliveredFor(other.id))
```

- [ ] **Step 2: Run the focused tests and record the expected failure**

Run: `rtk ./gradlew :kadre-x11:jvmTest --tests '*X11LoopContractTest*' :kadre-wayland:jvmTest --tests '*WaylandLoopContractTest*' --no-daemon --console=plain`

Expected: the close-during-batch tests expose queued delivery after `close()`, and the final-output test observes the current synthetic monitor.

- [ ] **Step 3: Tombstone ownership before queueing native close**

Update `closeWindow` to atomically remove current ownership and queued work before publishing the close command. Never restore ownership after a wake failure; retain the failure on the command and throw at the safe loop boundary. Native destruction stays in the owner loop thread.

```kotlin
if (!owner.tombstone.compareAndSet(false, true)) return AlreadyClosed
purgeOwnerEventsLocked(owner)
eventQueue.add(closeCommand)
signalWakeOrRecordFailure(closeCommand)
```

- [ ] **Step 4: Model Wayland discovery separately from known-empty outputs**

Track whether the registry has completed initial output discovery. Return a synthetic monitor only before that point; return the mapped live outputs, including an empty list after `global_remove` removes the final output. Update KDoc to document the user-approved behaviour.

- [ ] **Step 5: Run close and monitor regression suites**

Run: `rtk ./gradlew :kadre-x11:jvmTest :kadre-wayland:jvmTest --no-daemon --console=plain`

Expected: terminal-close, native-close race, wake-failure, multi-output, and final-removal tests pass.

- [ ] **Step 6: Commit the backend lifecycle work**

```bash
rtk git add kadre-core kadre-x11 kadre-wayland
rtk git commit -m "fix: make Linux window close terminal"
```

### Task 3: Preserve immutable WaitUntil deadlines across Web and Linux

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/BrowserScheduler.kt:73-104`
- Modify: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebEventLoopTest.kt:82-170`
- Modify: `kadre-x11/src/jvmMain/kotlin/org/graphiks/kadre/x11/X11EventLoop.kt:1320-1420`
- Modify: `kadre-x11/src/jvmTest/kotlin/org/graphiks/kadre/x11/X11LoopContractTest.kt`
- Modify: `kadre-wayland/src/jvmMain/kotlin/org/graphiks/kadre/wayland/WaylandEventLoop.kt:920-960,1260-1300`
- Modify: `kadre-wayland/src/jvmTest/kotlin/org/graphiks/kadre/wayland/WaylandLoopContractTest.kt`

**Interfaces:**
- Consumes: `ControlFlow.WaitUntil.instant` and `StartCause.ResumeTimeReached(requestedResume, start)`.
- Produces: no deadline iteration before the observed epoch reaches `instant`; rearming retains the original instant.

- [ ] **Step 1: Add failing early-fire and long-delay tests**

Add a Web fake timer test that fires while `epochNowMillis() < deadline` and asserts no RAF/iteration is delivered until a rearmed timeout fires at or after the deadline. Add X11 and Wayland tests with a remaining duration greater than `Int.MAX_VALUE` milliseconds and an early poll timeout.

- [ ] **Step 2: Run the focused scheduler tests and record the expected failure**

Run: `rtk ./gradlew :kadre-web-common:allTests :kadre-x11:jvmTest --tests '*WaitUntil*' :kadre-wayland:jvmTest --tests '*WaitUntil*' --no-daemon --console=plain`

Expected: Web emits `ResumeTimeReached` from a premature timeout and Linux reports `Poll` after a clamped wait.

- [ ] **Step 3: Re-arm instead of emitting a premature deadline**

In `BrowserScheduler.requestDeadline`, sample the epoch in the timer callback; if it is early, request only the remaining timeout using the original deadline and generation. In X11 and Wayland, retain the original `WaitUntil` after a clamped poll and use a non-terminal cause until the observed time reaches it.

```kotlin
val observed = nowMillis()
if (observed < deadline) {
    requestDeadline(deadline)
    return
}
deliver(StartCause.ResumeTimeReached(deadline, observed))
```

- [ ] **Step 4: Run the full Web/X11/Wayland suites**

Run: `rtk ./gradlew :kadre-web-common:allTests :kadre-x11:jvmTest :kadre-wayland:jvmTest --no-daemon --console=plain`

Expected: JS and Wasm parity plus all JVM scheduler tests pass.

- [ ] **Step 5: Commit deadline correctness**

```bash
rtk git add kadre-web-common kadre-x11 kadre-wayland
rtk git commit -m "fix: prevent early WaitUntil delivery"
```

### Task 4: Align UIKit lifecycle with the canonical traces

**Files:**
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/KadreAppDelegate.kt:10-62,73-130`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitActiveEventLoop.kt`
- Modify: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UIKitLifecycleTest.kt:35-250`
- Modify: `docs/kadre/specs.md` and `docs/kadre/specs.fr.md` if they describe the previous UIKit order

**Interfaces:**
- Consumes: the canonical lifecycle table and `ApplicationHandler` callbacks.
- Produces: `runLifecycleIteration(startCause, callbacks)` or an equivalent internal helper that owns `newEvents` and `aboutToWait`.

- [ ] **Step 1: Replace the current lifecycle trace assertions with the normative traces**

Extend `UIKitLifecycleTest` to record `resumed`, `newEvents`, surfaces, focus/occlusion, `suspended`, and `aboutToWait`. Assert each startup, background/foreground, duplicate delegate, and termination sequence exactly matches the table.

- [ ] **Step 2: Run the targeted simulator test and record the expected failure**

Run: `rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitLifecycleTest' --no-daemon --console=plain`

Expected: startup currently calls `canCreateSurfaces` before `resumed`, and termination has no `suspended` or iteration boundary.

- [ ] **Step 3: Add one internal lifecycle-iteration gateway**

Route each orchestrator method through one helper which emits the specified `StartCause`, invokes the table's callbacks in order, and always emits `aboutToWait` before terminal `exit()`. Guard transitions so duplicate UIKit notifications emit nothing.

```kotlin
private fun iterate(cause: StartCause, body: () -> Unit) {
    handler.newEvents(this, cause)
    body()
    handler.aboutToWait(this)
}
```

Keep startup's leading `resumed` outside this helper, as mandated by the canonical startup row.

- [ ] **Step 4: Run UIKit lifecycle and scheduler tests**

Run: `rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitLifecycleTest' --tests '*UIKitSchedulerTest' --no-daemon --console=plain`

Expected: no lifecycle order, duplicate callback, cleanup, or scheduler regression.

- [ ] **Step 5: Commit the UIKit change**

```bash
rtk git add kadre-uikit docs/kadre/specs.md docs/kadre/specs.fr.md
rtk git commit -m "fix: align UIKit lifecycle callbacks"
```

### Task 5: Probe usable Linux backends and preserve native failures

**Files:**
- Modify: `kadre/src/jvmMain/kotlin/org/graphiks/kadre/LinuxBackendDetector.kt:20-90`
- Modify: `kadre/src/jvmMain/kotlin/org/graphiks/kadre/EventLoop.jvm.kt:45-90`
- Modify: `kadre/src/jvmTest/kotlin/org/graphiks/kadre/LinuxBackendDetectorTest.kt:1-110`
- Create: `kadre/src/jvmTest/kotlin/org/graphiks/kadre/LinuxBackendLaunchTest.kt`

**Interfaces:**
- Consumes: `KADRE_LINUX_BACKEND`, `WAYLAND_DISPLAY`, and `DISPLAY`.
- Produces: a candidate result containing `backendClass`, the classpath/probe/launch stage, and its `Throwable?`; auto selection can fall back, forced selection cannot.

- [ ] **Step 1: Add failing selection tests through injected environment and probe seams**

Introduce test-only constructor parameters or internal function arguments for environment lookup, class loading, and a probe lambda. Test auto Wayland-probe failure followed by X11 success, forced Wayland failure without fallback, and an aggregate error retaining a primary plus suppressed native cause.

```kotlin
val selection = detectBackend(
    environment = mapOf("WAYLAND_DISPLAY" to "stale", "DISPLAY" to ":0"),
    probe = { backend -> if (backend == WAYLAND) fail("stale") else success() },
)
assertEquals(X11, selection.backend)
```

- [ ] **Step 2: Run the detector tests and record the expected failure**

Run: `rtk ./gradlew :kadre:jvmTest --tests '*LinuxBackendDetectorTest' --tests '*LinuxBackendLaunchTest' --no-daemon --console=plain`

Expected: existing detection only validates class loading and cannot represent a failed connection or forced-mode policy.

- [ ] **Step 3: Implement staged candidate selection and launch unwrapping**

In auto mode, order candidates from session hints, test classpath, probe a usable connection, then launch. For a forced override, run exactly the forced candidate and return its descriptive failure. In `EventLoop.jvm.kt`, catch `InvocationTargetException` and rethrow its target cause with context rather than the reflection wrapper.

- [ ] **Step 4: Run the facade regression suite**

Run: `rtk ./gradlew :kadre:jvmTest --no-daemon --console=plain`

Expected: auto fallback, override, missing backend, and unwrapped-cause tests pass.

- [ ] **Step 5: Commit the facade change**

```bash
rtk git add kadre/src/jvmMain kadre/src/jvmTest
rtk git commit -m "fix: select usable Linux backends"
```

### Task 6: Deliver deterministic CI, docs, and 19/19 closure evidence

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/linux-compose-visual.yml`, `.github/workflows/android-visual.yml`, `.github/workflows/ios-visual.yml`, and `.github/workflows/macos-visual.yml`
- Create: `.github/workflows/cross-platform-correctness.yml`
- Create: `scripts/test-linux-container.sh`
- Create: `scripts/android-emulator-test.sh`
- Create: `scripts/verify-test-results.py`
- Create: `scripts/check-workflow-contract.py`
- Create: `scripts/test-workflow-contract.sh`
- Modify: `scripts/test-web-browsers.sh`
- Create: `docs/kadre/cross-platform-correctness-report.md`
- Modify: `CHANGELOG.md`, `docs/kadre/specs.md`, `docs/kadre/specs.fr.md`, `docs/kadre/tutorials/linux-app.md`, and `docs/kadre/tutorials/linux-app.fr.md`

**Interfaces:**
- Consumes: the Task 1–5 test commands and the required PR matrix in the closure spec.
- Produces: blocking `cross-platform-correctness` aggregate, script-owned result validation, and exactly nineteen traceability rows.

- [ ] **Step 1: Write failing workflow-contract fixtures and report-validator fixtures**

Create a workflow fixture containing a required job with `continue-on-error: true`, a path filter, and a missing command; make `scripts/test-workflow-contract.sh` assert that `check-workflow-contract.py` rejects it. Create a 18-row report fixture and assert that the report validator rejects it.

```bash
rtk scripts/test-workflow-contract.sh
# Expected before implementation: reports that the checker/validator is missing.
```

- [ ] **Step 2: Implement the script-owned validation boundary**

Make `verify-test-results.py` require a non-zero test count, zero deterministic skips/failures, and mandatory PNG decoding/dimensions/non-background pixels. Make `check-workflow-contract.py` require every matrix job, no required-job path filter, no masking expression, a timeout at most 25 minutes, and the aggregate status dependency.

- [ ] **Step 3: Add the blocking PR matrix**

Create or update the PR workflow to run host contracts, browser JS/Wasm, iOS simulator UIKit tests, Android emulator tests, glibc and musl X11/Wayland checks, and deterministic captures. Remove success masking from required visual workflows; optional hardware diagnostics must be separate jobs and never satisfy the aggregate.

- [ ] **Step 4: Add runtime and compatibility documentation**

Document connection-based Linux auto selection, forced override semantics, X11/Wayland runtime dependencies, current Web event naming, and all six approved compatibility changes. Create `cross-platform-correctness-report.md` with exactly nineteen numbered rows, each containing the finding, test/command, environment, result, and proof path.

- [ ] **Step 5: Run docs and workflow-contract gates**

Run: `rtk scripts/test-workflow-contract.sh && rtk python3 scripts/verify-test-results.py --help && rtk rg -n "continue-on-error: true|\\|\\| true|\\|\\| echo" .github/workflows scripts`

Expected: fixtures pass, validators expose usage successfully, and any remaining masking match is confined to an explicitly optional hardware diagnostic that the contract checker excludes.

- [ ] **Step 6: Commit CI and documentation**

```bash
rtk git add .github scripts docs CHANGELOG.md
rtk git commit -m "ci: enforce cross-platform correctness gates"
```

### Task 7: Run the integration matrix, independent review, and publication gate

**Files:**
- Modify if required by verification: only the owning task's files.
- Verify: `docs/kadre/cross-platform-correctness-report.md`, all Task 1–6 paths, and the closure spec.

**Interfaces:**
- Consumes: every Task 1–6 commit.
- Produces: fresh verification evidence, an independent review with no unresolved Critical/Important findings, and a fast-forward-only push.

- [ ] **Step 1: Run the host and browser matrix**

Run:

```bash
rtk ./gradlew :kadre-core:jvmTest :kadre-test:allTests :kadre-appkit:jvmTest :kadre-x11:jvmTest :kadre-wayland:jvmTest :kadre-android:testAndroidHostTest :kadre-web-common:allTests :kadre:build --no-daemon --stacktrace --console=plain
rtk scripts/test-web-browsers.sh
rtk scripts/test-workflow-contract.sh
rtk git diff --check origin/codex/cross-platform-correctness-design...HEAD
```

Expected: every command exits 0 and the diff check is empty.

- [ ] **Step 2: Run supported native integrations**

Run:

```bash
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-uikit-simulator.sh
rtk scripts/test-x11-xvfb.sh
rtk scripts/test-linux-container.sh glibc
rtk scripts/test-linux-container.sh musl
rtk scripts/android-emulator-test.sh
```

Expected: every available environment exits 0. If this host lacks a required runner, record that exact limitation in the closure report and rely on the corresponding blocking CI job; never record it as a pass.

- [ ] **Step 3: Audit the nineteen findings**

Check every row in `docs/kadre/cross-platform-correctness-report.md` has a unique number 1–19, a real test/command, the correct environment, a recorded result, and an evidence path. Re-run the report validator after the audit.

- [ ] **Step 4: Request an independent review**

Give a reviewer the closure spec, the full range from `9bdc7f1e` to current `HEAD`, Task 1–6 commands/output, and the report. The reviewer must inspect code and tests read-only and classify each finding. Correct any Critical or Important finding, then repeat its owning verification before another review.

- [ ] **Step 5: Commit any review correction and verify the push target**

```bash
rtk git status --short
rtk git fetch origin codex/cross-platform-correctness-design
rtk git merge-base --is-ancestor origin/codex/cross-platform-correctness-design HEAD
rtk git log --oneline origin/codex/cross-platform-correctness-design..HEAD
```

Expected: a clean worktree, a fast-forward relation, and the intended commits listed. Stop if the remote branch has advanced incompatibly.

- [ ] **Step 6: Push without force**

```bash
rtk git push origin HEAD:refs/heads/codex/cross-platform-correctness-design
```

Expected: a fast-forward push only after fresh verification and a review without unresolved Critical or Important findings.
