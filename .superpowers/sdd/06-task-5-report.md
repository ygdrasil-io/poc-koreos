# Plan 06 — Task 5 UIKit scheduler and safe-area report

## Status

DONE — ready for independent review.

UIKit now owns one demand-driven scheduler per active event loop instead of one
perpetual `CADisplayLink` per window. Redraws coalesce by live `WindowId`, proxy
wakes execute complete loop iterations on the main queue, and `WaitUntil` uses
Unix epoch millisecond deadlines protected by monotonically increasing
generations. UIKit safe-area insets are now returned in physical pixels using
the window's current scale and `roundToInt()` for every edge.

Baseline: `7da017db484d202cf217d691a5a2e9e851b773ed`.

No public signature changed. The only core change is the `Window.safeArea` KDoc
clarification required by the task. `iosX64()` remains in the UIKit library.

## Implementation

- `UIKitLoopState.kt`
  - owns live/terminal window IDs, insertion-ordered redraw coalescing, pending
    start causes, and generation-guarded `WaitUntil` state;
  - classifies external wakes as `WaitCancelled(deadline)` under `Wait` /
    `WaitUntil` and as `Poll` under `Poll`;
  - rejects redraws for closed IDs and revalidates every captured redraw before
    dispatch;
  - makes close an earlier event for remaining windows while cancelling the
    closed ID's redraw and the current deadline generation;
  - clears pending work/deadlines after handler failure or exit.
- `UIKitScheduler.kt`
  - injects clock, display-link, and timer operations that are used by both the
    deterministic tests and production adapter;
  - owns one reusable loop-level `CADisplayLink`, removed from the run loop when
    idle and invalidated only on loop exit;
  - schedules epoch deadlines with `dispatch_after` on the main queue; stale
    blocks are harmless because their captured generation must still be current;
  - runs every successful tick in exact order:
    `newEvents -> live redraws -> aboutToWait -> arm/stop`;
  - distinguishes redraws queued during `newEvents` (eligible for the current
    tick) from redraws queued during redraw/about-to-wait callbacks (next tick);
  - stops and clears scheduling state before propagating any handler exception.
- `UIKitActiveEventLoop.kt`
  - owns the single scheduler and transactionally registers it with each live
    window;
  - removes a window from the Task 4 registry, adjusts the recreation cursor,
    and cancels scheduler work before the existing aggregated close stages;
  - routes control-flow changes, exit, proxies, and window redraw requests to
    the scheduler without changing Task 4 lifecycle/reuse/terminal semantics.
- `UIKitEventLoopProxy.kt`
  - retains atomic main-queue coalescing;
  - its dispatched block resets the flag then calls only
    `scheduler.wakeExternal()`; it never calls `handler.newEvents` directly;
  - accepts an injected main-queue dispatcher used by the deterministic proxy
    test and a production `dispatch_async` default.
- `UiKitWindow.kt`
  - no longer owns or retains a display-link target;
  - `requestRedraw()` queues its ID with the loop scheduler;
  - uses `physicalInset(points, scale)` and `physicalSafeArea(...)` with the
    current `uiWindow.screen.scale` for all safe-area edges;
  - returns zero when points or scale are negative/non-finite, and also guards
    a non-finite multiplication result.
- `Window.kt`
  - documents that safe-area values are physical pixels obtained with the
    current window scale and nearest-integer edge rounding.

## Strict TDD evidence

Every Gradle command used:

```text
GRADLE_USER_HOME=/Volumes/Cache/poc-koreos/.gradle-plan06-uikit-task5
```

Every behavioral run used `iosSimulatorArm64` and `--rerun-tasks`. Tests use a
fake epoch clock and fake main-queue/display-link/timer operations; no sleep or
real timer is used.

### Safe area

1. RED — the first `UIKitSafeAreaTest` compile exited 1 after 7m 1s with 17/17
   tasks executed. `physicalInset` and `physicalSafeArea` were unresolved.
2. The first implementation run correctly exposed a bad fixture expectation:
   `0.75 * 3.0` rounds to 2, not 3. Only that fixture was corrected to
   `0.9 * 3.0`.
3. GREEN — 5/5 tests passed, exit 0, `BUILD SUCCESSFUL in 10s`, 32/32 tasks.
   Coverage includes the named `physicalInset(10.25, 3.0) == 31` contract,
   fractions on four edges, scale changes without recreation, zero, negatives,
   NaN, and positive/negative infinity for both inputs.

### Demand-driven display link and Poll

1. RED — initial scheduler tests failed compilation on missing
   `UIKitScheduler` / `UIKitSchedulerOperations`: exit 1,
   `BUILD FAILED in 4s`, 17/17 tasks.
2. GREEN — idle `Wait` and one coalesced redraw/frame/stop passed: exit 0,
   `BUILD SUCCESSFUL in 9s`, 32/32 tasks.
3. RED — `Poll` test failed compilation on missing `controlFlowChanged()`:
   exit 1, `BUILD FAILED in 4s`, 17/17 tasks.
4. GREEN — Poll stays active and switching to Wait stops it: exit 0,
   `BUILD SUCCESSFUL in 9s`, 32/32 tasks.

### Proxy and exact three-cycle ordering

1. RED — the fake-main-queue test failed because the legacy proxy accepted
   only `UIKitActiveEventLoop`, not `scheduler` / `dispatchMain`: exit 1,
   `BUILD FAILED in 5s`, 17/17 tasks.
2. The first production integration compile isolated one Native binding issue:
   `NSDate.timeIntervalSince1970` is a top-level Foundation extension. KLIB
   metadata confirmed the declaration, an explicit import fixed the root cause,
   and no clock fallback or test hook was added.
3. GREEN — three distinct proxy wake/drain cycles each produced exactly
   `newEvents -> queued redraw -> aboutToWait`: exit 0,
   `BUILD SUCCESSFUL in 11s`, 32/32 tasks.
4. Self-review RED — invoking an already captured frame exposed a latent extra
   `WaitCancelled` left by redraws consumed from `newEvents`: exit 1,
   `BUILD FAILED in 8s`, 32/32 tasks.
5. GREEN — current-tick and next-tick redraw phases are now distinct: exit 0,
   `BUILD SUCCESSFUL in 9s`, 32/32 tasks; the stale frame adds no iteration.

### WaitUntil generations and epoch causes

1. RED — no timer was armed (`NoSuchElementException`): exit 1,
   `BUILD FAILED in 9s`, 32/32 tasks.
2. GREEN — one future epoch deadline arms, an external earlier event yields
   `WaitCancelled(deadline)`, a new generation rearms, and the stale first block
   does nothing: exit 0, `BUILD SUCCESSFUL in 8s`, 32/32 tasks.
3. RED — firing the current deadline produced no iteration: exit 1,
   `BUILD FAILED in 7s`, 32/32 tasks.
4. GREEN — at fake epoch `deadline + 7`, the exact cause is
   `ResumeTimeReached(deadline, deadline + 7)` and the same consumed deadline is
   not rearmed: exit 0, `BUILD SUCCESSFUL in 9s`, 32/32 tasks.
5. RED — repeated registration/control-flow notifications scheduled multiple
   blocks for the same current deadline: exit 1, `BUILD FAILED in 7s`, 32/32.
6. GREEN — the state returns a keep decision for the already-current deadline:
   exit 0, `BUILD SUCCESSFUL in 7s`, 32/32 tasks.
7. Self-review RED — a redraw requested from `aboutToWait` could leave a logical
   generation without scheduling its native block: exit 1,
   `BUILD FAILED in 9s`, 32/32 tasks.
8. GREEN — pending frame work cancels the armed generation and the deadline is
   really rearmed after that frame: exit 0, `BUILD SUCCESSFUL in 7s`, 32/32.
9. RED/GREEN — external wake under Poll initially reported WaitCancelled;
   focused RED failed in 8s, and GREEN reports `StartCause.Poll` in 8s, both with
   32/32 tasks executed.

### Close, liveness, and failures

1. RED — terminal scheduler close had no observable success/idempotence result
   (`Unit` where Boolean was expected): exit 1, `BUILD FAILED in 4s`, 17/17.
2. GREEN — close cancels a pending redraw and deadline, rejects later redraw and
   repeated close, stops the link, and stale frame/timer callbacks produce no
   events: exit 0, `BUILD SUCCESSFUL in 8s`, 32/32 tasks.
3. RED — a throwing `newEvents` left the display link active: exit 1,
   `BUILD FAILED in 10s`, 32/32 tasks.
4. GREEN — the exact throwable is propagated after pending scheduler state is
   cleared and the display link stopped: exit 0, `BUILD SUCCESSFUL in 8s`,
   32/32 tasks.
5. Reentrant close coverage passed: closing the second target from the first
   redraw callback prevents dispatch to the captured-but-no-longer-live ID;
   exit 0, `BUILD SUCCESSFUL in 7s`, 32/32 tasks.
6. Self-review RED — closing one of two windows invalidated the current deadline
   without rearming it for the survivor: exit 1, `BUILD FAILED in 9s`, 32/32.
7. GREEN — close is an earlier event, starts one WaitCancelled frame, never
   targets the closed ID, and rearms WaitUntil for the remaining live window:
   exit 0, `BUILD SUCCESSFUL in 9s`, 32/32 tasks.

## Final verification

### Focused scheduler and safe area

```text
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test \
  --tests '*UIKitSchedulerTest' --tests '*UIKitSafeAreaTest' --rerun-tasks
```

Exit 0, `BUILD SUCCESSFUL in 10s`, 32/32 tasks executed; 18/18 tests
(scheduler 13, safe area 5), 0 skipped/failures/errors.

### Full UIKit suite

```text
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --rerun-tasks
```

Exit 0, `BUILD SUCCESSFUL in 14s`, 32/32 tasks executed; 44/44 tests,
0 skipped/failures/errors:

- `UIKitLifecycleTest`: 16
- `UIKitGestureMapperTest`: 6
- `UIKitSchedulerTest`: 13
- `UIKitSafeAreaTest`: 5
- `UiKitKeyMapperTest`: 2
- `UiKitWindowNoOpTest`: 2

### Cross-compilation and sample

```text
rtk ./gradlew \
  :kadre-uikit:compileKotlinIosArm64 \
  :kadre-uikit:compileKotlinIosX64 \
  :samples:hello-touch:compileKotlinIosArm64 \
  :samples:hello-touch:compileKotlinIosSimulatorArm64 \
  --rerun-tasks
```

Exit 0, `BUILD SUCCESSFUL in 8s`, 34/34 tasks executed. The UIKit `iosX64`
library compiled; only the Intel test executable remains correctly disabled on
the ARM64 host.

## Self-review

- Task 4 registry removal and recreation-cursor adjustment still precede all
  cleanup. Scheduler close is non-throwing and occurs before invalidate /
  `Destroyed` / hide-resign aggregation, so no pending frame can target the ID.
- Task 4 lifecycle/reuse, terminal admission, rollback, live snapshots, and
  first-failure-plus-suppressed cleanup code were not weakened; all 16 lifecycle
  tests pass in the fresh full gate.
- `UiKitWindow` contains no `CADisplayLink` field, target, creation, or invalidation.
  The sole native owner is `UIKitNativeSchedulerOperations`.
- Every proxy main-queue block calls only `scheduler.wakeExternal()` after its
  atomic reset. Handler callbacks exist only inside the scheduler tick.
- All timer timestamps and start-cause values are Unix epoch milliseconds.
- Deadline blocks capture a monotonically increasing generation; earlier event,
  close, control-flow change, failure, and exit all make stale blocks harmless.
- Production uses every injected operation; no test-only method or sleep was
  added.
- The public core type/API shape is unchanged. `Window.safeArea` already returns
  `Insets<Int>` and now explicitly documents physical scaling/rounding.
- `kadre-uikit/build.gradle.kts:21` still contains `iosX64()`.
- `.superpowers/sdd/progress.md` was not modified.
- `git diff --check` passed before the gates. A fresh final check is recorded
  after this report and before commit.

Expected pre-existing warnings remain: Dokka V1 deprecation, the ARM64 host's
disabled `iosX64Test`, and two `UIKitScreenCapturer.kt` redundant conversion /
cast warnings. No new production warning was emitted.

## Commit

Created local commit `22dad5c` (`fix(uikit): schedule on demand and scale safe
area`). The final cached diff check passed, and no push was performed.

---

## Corrective review wave 1 — 2026-07-19

### Status and commit

DONE — all 10 Important and 3 Minor review findings are implemented and
verified from base `22dad5cbb67639fe33d61ae31bc52b9ee4141d03`.

Implementation commit: `6f4e23a` (`fix(uikit): harden scheduler lifecycle`).
No amend and no push were performed.

### Implementation summary by finding

1. A non-terminal proxy wake is accepted with zero live windows and runs the
   normal `newEvents -> redraw work -> aboutToWait` iteration. Exit makes later
   wakes inert.
2. Proxy, redraw, and close signals sample the epoch clock and upgrade a pending
   cancellation to `ResumeTimeReached(deadline, observedAt)` once due; the
   before-deadline case remains `WaitCancelled(deadline)`.
3. An early native deadline callback consumes its generation and immediately
   arms a new generation for the same epoch deadline. Repeated early callbacks
   use the same recursive production path.
4. `deadlineDelayNanos` compares deadline/now before subtraction, saturates an
   overflowing positive millisecond difference, and saturates nanoseconds.
5. Every display-link activation has a scheduler generation. Stop invalidates
   it before the native operation; the reusable native target receives the new
   callback on reactivation.
6. Loop state and scheduler exit are terminal and idempotent. Registration is
   rejected; wake, redraw, timer, frame, and control-flow paths are inert.
   `UIKitActiveEventLoop.createWindow` rejects before logical/native allocation.
7. `UIKitLifecycleOrchestrator.willTerminate` aggregates loop exit after window
   and surface cleanup, so the loop display link is disposed exactly once even
   when `Destroyed` fails.
8. Deadline blocks capture only `WeakReference<UIKitScheduler>` plus the value
   generation/deadline; a retained far-future fake timer no longer retains the
   scheduler/handler/event loop.
9. First-window scheduling registration is transactional. State rollback makes
   the ID reusable, registration exceptions are inside native creation rollback,
   and failed registration leaves the UIKit registry live long enough for the
   normal close stages to run. Close aggregates scheduler, invalidate,
   `Destroyed`, and hide/resign failures in flat first/suppressed order.
10. `UiKitWindow.id` is a loop-owned logical monotonic ID (`1, 2, 3, ...`),
    allocated through a guard that rejects exhaustion before native allocation.
    Pure loop state rejects concurrently live duplicates but has no permanent
    tombstone and permits the same value after close.
11. Proxy dispatch failure restores the atomic pending flag. Multiple pending
    wakes coalesce to one block, and resetting before the real scheduler callback
    permits exactly one reentrant follow-up block in observable callback order.
12. Redraw and `aboutToWait` exceptions propagate the identical throwable,
    invalidate the active frame generation, and purge reentrant work. A failure
    while stopping the display link is suppressed on the handler throwable.
13. Valid non-negative finite inputs whose product is `+Infinity`, or finite
    above `Int.MAX_VALUE`, saturate to `Int.MAX_VALUE`; invalid individual
    inputs and negatives still return zero, and ordinary values still round.

### Strict RED/GREEN evidence

Every behavioral run used this exact deterministic command shape (the quoted
filter was replaced by the test name shown below):

```text
rtk env GRADLE_USER_HOME=/Volumes/Cache/poc-koreos/.gradle-plan06-uikit-task5-fixes \
  ./gradlew :kadre-uikit:iosSimulatorArm64Test \
  --tests '*<test-filter>' --rerun-tasks --no-daemon
```

No test uses `sleep` or wall-clock timing.

1. Zero-window proxy wake: RED `proxyWakeWithNoWindowsRunsAnIterationThatCanCreateAndObserveQueuedWork`,
   1/1 failed, `BUILD FAILED in 6m 23s`, 32 tasks (fresh cache downloaded
   Gradle); wake was rejected. GREEN 1/1, `BUILD SUCCESSFUL in 37s`, 32/32.
2. Deadline classification: RED `proxyRedrawAndCloseAtOrAfterDeadlineReportResumeTimeReached`,
   1/1 failed, `BUILD FAILED in 34s`; signals emitted `WaitCancelled`. GREEN
   1/1, `BUILD SUCCESSFUL in 33s`, 32/32. The existing before-deadline test
   continues to assert `WaitCancelled(deadline)`.
3. Early callback: RED `earlyDeadlineCallbackAfterClockMovesBackwardArmsAReplacementGeneration`,
   1/1 failed, `BUILD FAILED in 31s`; no replacement timer existed. GREEN 1/1,
   `BUILD SUCCESSFUL in 30s`, 32/32.
4. Relative overflow: RED `nativeDeadlineDelaySaturatesPastAndFarFutureBoundsWithoutOverflow`,
   compile failed on the missing pure helper, `BUILD FAILED in 30s`, 17/17.
   GREEN 1/1, `BUILD SUCCESSFUL in 29s`, 32/32; `Long.MIN_VALUE` past is zero
   and `Long.MAX_VALUE` future saturates to `Long.MAX_VALUE` nanos.
5. Frame generations: RED `frameCapturedBeforeStopCannotConsumeWorkFromAReactivatedGeneration`,
   1/1 failed, `BUILD FAILED in 30s`; the old callback consumed the new redraw.
   GREEN 1/1, `BUILD SUCCESSFUL in 31s`, 32/32.
6. Permanent exit: RED `exitIsPermanentIdempotentAndPreventsEverySchedulingPathFromRestarting`,
   1/1 failed, `BUILD FAILED in 26s`; dispose/re-registration were not terminal.
   GREEN 1/1, `BUILD SUCCESSFUL in 26s`, 32/32.
7. Terminal orchestration: RED `terminationDisposesTheLoopSchedulerOnceEvenWhenWindowDestructionFails`,
   compile failed because the operations seam did not exist, `BUILD FAILED in
   22s`, 17/17. GREEN 1/1, `BUILD SUCCESSFUL in 35s`, 32/32.
8. Timer lifetime: RED `retainedDeadlineCallbackDoesNotKeepItsSchedulerOwnerAlive`,
   1/1 failed after forced Native GC, `BUILD FAILED in 30s`; the weak probe still
   resolved the scheduler. GREEN 1/1, `BUILD SUCCESSFUL in 38s`, 32/32; the weak
   probe is null and firing the retained timer is inert.
9. Transactional behavior used four focused cycles:
   - scheduler state RED `registrationSchedulingFailureRollsBackStateSoTheSameIdCanBeRegisteredLater`,
     failed in 34s; GREEN in 31s;
   - helper registration boundary RED `registrationFailureRollsBackTheCreatedStructureAndPreservesFailures`,
     failed in 31s; GREEN in 26s;
   - actual ActiveEventLoop creation seam RED `schedulerRegistrationFailureRemovesAndClosesTheNativeWindowCandidate`,
     failed in 35s; GREEN in 33s;
   - close aggregation RED `schedulerCloseFailureStillInvalidatesDispatchesDestroyedAndPreservesSuppressedFailures`,
     failed in 33s; GREEN in 36s.
   Every GREEN was 1/1 with 32/32 tasks.
10. Logical identity used two cycles:
    - tombstone RED `loopStateRejectsConcurrentDuplicateIdsButAllowsReuseAfterClose`,
      failed in 30s; GREEN in 29s;
    - loop IDs RED `windowIdsAreLoopOwnedMonotonicAndNeverReuseAClosedNativeHandleIdentity`,
      failed in 33s; adding the exhaustion assertion gave the expected missing
      helper compile RED in 22s/17 tasks; combined GREEN was 1/1 in 32s/32 tasks.
11. Proxy CAS:
    - dispatch failure RED `proxyRestoresItsCasFlagWhenMainQueueDispatchThrows`,
      failed in 29s; GREEN 1/1 in 26s;
    - coalescing/reentrancy passed on first coverage run in 28s because the
      reset-before-callback behavior already existed. A final mutation check
      moved the reset after the callback: the exact test then RED failed in 29s;
      restoring the committed ordering GREEN passed 1/1 in 26s/32 tasks, with a
      clean worktree after restoration.
12. Redraw/about-to-wait coverage initially passed 2/2 in 26s because base
    abort logic plus the new frame generations already met the assertions. The
    stronger injected stop-failure assertion produced RED for
    `redrawFailurePropagatesExactlyStopsItsFrameAndPurgesReentrantWork` in 26s;
    preserving the handler throwable and suppressing the stop failure GREEN
    passed 1/1 in 26s/32 tasks.
13. Safe-area overflow: RED `positiveProductOverflowAndFiniteOversizeSaturateWhileNormalValuesRound`,
    1/1 failed in 26s because positive product infinity became zero. GREEN 1/1,
    `BUILD SUCCESSFUL in 28s`, 32/32.

### Fresh required gates

Focused gate after the final production fix:

```text
rtk env GRADLE_USER_HOME=/Volumes/Cache/poc-koreos/.gradle-plan06-uikit-task5-fixes \
  ./gradlew :kadre-uikit:iosSimulatorArm64Test \
  --tests '*UIKitSchedulerTest' --tests '*UIKitSafeAreaTest' \
  --tests '*UIKitLifecycleTest' --rerun-tasks --no-daemon
```

Exit 0, `BUILD SUCCESSFUL in 33s`, 32/32 tasks; 53/53 tests, zero skipped,
failures, or errors:

- `UIKitSchedulerTest`: 26
- `UIKitLifecycleTest`: 21
- `UIKitSafeAreaTest`: 6

Full UIKit and cross-target gate after the final production fix:

```text
rtk env GRADLE_USER_HOME=/Volumes/Cache/poc-koreos/.gradle-plan06-uikit-task5-fixes \
  ./gradlew :kadre-uikit:iosSimulatorArm64Test \
  :kadre-uikit:compileKotlinIosArm64 \
  :kadre-uikit:compileKotlinIosX64 \
  :samples:hello-touch:compileKotlinIosArm64 \
  :samples:hello-touch:compileKotlinIosSimulatorArm64 \
  --rerun-tasks --no-daemon
```

Exit 0, `BUILD SUCCESSFUL in 36s`, 50/50 tasks. The full UIKit suite is
63/63 with zero skipped/failures/errors: scheduler 26, lifecycle 21, safe area
6, gesture mapper 6, key mapper 2, and window no-op 2. UIKit `iosArm64` and
library `iosX64` compile; both requested hello-touch sample targets compile.

Final hygiene commands:

```text
rtk git diff --check
rtk git diff --cached --check
rtk rg -n "iosX64\\(\\)" kadre-uikit/build.gradle.kts samples/hello-touch/build.gradle.kts
rtk rg -n "sleep|Thread\\.sleep" <three modified UIKit test files>
rtk git diff -- kadre-uikit/api/kadre-uikit.klib.api
```

Both diff checks exited 0 with no output. `iosX64()` appears at
`kadre-uikit/build.gradle.kts:21` and is absent from hello-touch. Sleep search
and public KLIB API diff produced no output.

Expected pre-existing warnings only: Dokka V1 deprecation, disabled
`iosX64Test` on the ARM64 host, and the two existing
`UIKitScreenCapturer.kt` redundant conversion/cast warnings. The temporary
unused-expression warning observed during the close implementation was removed;
it is absent from both fresh final gates.

### Self-review

- Re-read all 13 findings and binding decisions against the final diff; no
  finding is deferred and no additional breaking behavior was introduced.
- Ordinary last-window close remains non-terminal. A later zero-window proxy
  wake runs normally, and a replacement registered from close callbacks is
  scheduled from the pending close cause.
- Direct exit and lifecycle termination are distinct but converge on the same
  idempotent scheduler terminal state; no display-link restart path remains.
- Early/stale/reached timer paths all use generations; retained timer closures
  have no strong scheduler owner path.
- Registration rollback changes only first-window scheduling state. Adding a
  window to an already running loop does not disturb an existing deadline.
- Close removes registry membership first, then aggregates scheduler,
  invalidate, `Destroyed`, and hide/resign without hiding later failures.
- Task 4 recreation cursor, reentrancy, live-snapshot, rollback, terminal
  admission, first-failure, and suppressed-order contracts remain covered by
  all 21 lifecycle tests.
- Native pointers remain only raw handles/touch identities; window identity is
  logical and monotonic. A stale closed window ID cannot target a new pointer.
- No production test-only method, no public signature/API change, no sample
  target change, no sleep, and no wall-clock timing were added.

### Concerns

None. The final worktree after the proxy mutation check exactly matches
implementation commit `6f4e23a` plus this report update.
