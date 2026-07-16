# Apple Backends Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Make AppKit and UIKit lifecycle/scheduling exact, release native run-loop and delegate resources, prevent iOS window duplication, and validate real arm64 simulator behavior.

**Architecture:** AppKit uses a closeable Core Foundation run-loop owner that observes after-wait/before-wait phases, owns its timer, and reports exact start causes. UIKit uses a loop-level scheduler with a demand-driven `CADisplayLink`, generation-guarded deadline, and an internal surface-recreation session that reuses existing windows. Both backends filter all callbacks through live-window registries.

**Tech Stack:** Kotlin/JVM 25 FFM, AppKit/CoreFoundation/Objective-C runtime, Kotlin/Native, UIKit/QuartzCore/GCD, XCTest-compatible Kotlin tests, arm64 iOS simulator.

## Global Constraints

- Complete `01-contracts` first.
- Keep `iosX64` in `kadre-uikit`; only the Compose sample loses that target in plan 02.
- Do not add a public surface-restoration callback. Existing `canCreateSurfaces` is used with internal existing-window reuse.
- Never retain a Core Foundation object, Objective-C delegate entry, `CADisplayLink`, or timer without one idempotent release path.
- Exceptions at Objective-C/CF callbacks are captured and rethrown only at a Kotlin-safe boundary.
- Use `rtk` for shell commands and commit after each green task.

---

### Task 1: Specify AppKit iteration and deadline behavior with a fake run loop

**Files:**
- Create: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitLoopState.kt`
- Create: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/AppKitLoopStateTest.kt`
- Replace: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/CFRunLoopRedrawObserverTest.kt`

**Step 1: Write failing behavior tests**

Use a fake epoch clock and fake native operations to assert:

- startup is `resumed -> newEvents(Init) -> canCreateSurfaces -> aboutToWait`;
- an after-wait signal chooses `Poll`, `WaitCancelled(deadline?)`, or `ResumeTimeReached(deadline, now)` correctly;
- a timer firing before its deadline never reports `ResumeTimeReached`;
- an event cancels an armed deadline and preserves its `requestedResume` in `WaitCancelled`;
- an already-expired deadline produces one cause without repeatedly rearming a CF timer;
- redraws are coalesced and dispatched between `newEvents` and `aboutToWait`;
- close/exit suppresses all later callback dispatch.

Delete reflection/signature-only assertions that do not exercise ordering or ownership.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-appkit:jvmTest --tests '*AppKitLoopStateTest' --tests '*CFRunLoopRedrawObserverTest'
```

Expected: state component absent; existing no-op timer cannot distinguish a deadline from another wake.

**Step 3: Implement the pure state component**

Use explicit methods:

```kotlin
internal class AppKitLoopState(private val nowMillis: () -> Long) {
    fun requestRedraw(windowId: WindowId): Boolean
    fun arm(controlFlow: ControlFlow): TimerDecision
    fun signalExternalEvent()
    fun signalDeadline(generation: Long)
    fun beginIteration(): StartCause
    fun takeRedraws(): List<WindowId>
    fun closeWindow(windowId: WindowId)
    fun exit()
}
```

`TimerDecision` describes cancel/arm/fire-now; timer generation rejects stale callbacks. All time values remain Unix epoch milliseconds at this layer.

**Step 4: Run tests**

```bash
rtk ./gradlew :kadre-appkit:jvmTest --tests '*AppKitLoopStateTest' --tests '*CFRunLoopRedrawObserverTest'
```

Expected: deterministic state/ordering tests pass on any host.

**Step 5: Commit**

```bash
rtk git add kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitLoopState.kt kadre-appkit/src/jvmTest
rtk git commit -m "test(appkit): define lifecycle and deadline state"
```

---

### Task 2: Replace the global observer with a closeable CF run-loop owner

**Files:**
- Replace: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/CFRunLoopRedrawObserver.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitEventLoop.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitEventLoopProxy.kt`
- Create: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/CFRunLoopOwnerTest.kt`

**Step 1: Add a failing ownership trace test**

Inject a `CFRunLoopApi` and assert:

- install creates/adds one observer;
- rearming a deadline invalidates, removes, and releases the prior timer;
- deadline callback records the matching timer generation;
- `close()` orders `invalidate timer -> remove timer -> release timer -> remove observer -> release observer -> close arena`;
- a second close performs no native call;
- static callback routing is cleared, so a later callback is ignored.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-appkit:jvmTest --tests '*CFRunLoopOwnerTest'
```

Expected: current observer uses `Arena.global`, never removes/releases the observer, and invalidates but never releases timers.

**Step 3: Implement `CFRunLoopOwner : AutoCloseable`**

Resolve and inject bindings for:

```text
CFRunLoopAddObserver / CFRunLoopRemoveObserver
CFRunLoopAddTimer / CFRunLoopRemoveTimer
CFRunLoopObserverInvalidate / CFRunLoopTimerInvalidate
CFRunLoopWakeUp / CFRelease
```

Use `Arena.ofShared()` for callback stubs, retain the observer/timer refs as fields, and close in reverse ownership order. Install for both `kCFRunLoopAfterWaiting` and `kCFRunLoopBeforeWaiting`:

- after waiting: `newEvents(beginIteration())`;
- before waiting: drain valid redraws, call `aboutToWait`, then arm the chosen control flow.

Convert Unix milliseconds to `CFAbsoluteTime` only in the native adapter.

**Step 4: Route proxy wake through state**

`AppKitEventLoopProxy.wakeUp()` first marks an external event in the loop state, then calls `CFRunLoopWakeUp`. It must support three complete wake-consume cycles. `AppKitEventLoop.createProxy()` obtains the owner installed before `NSApp.run` rather than creating an unrelated static proxy.

**Step 5: Run tests**

```bash
rtk ./gradlew :kadre-appkit:jvmTest --tests '*AppKitLoopStateTest' --tests '*CFRunLoopOwnerTest' --tests '*AppKitEventLoopProxyTest'
```

Expected: callback phases, exact deadline cause, wake rearm, and native release traces pass.

**Step 6: Commit**

```bash
rtk git add kadre-appkit/src/jvmMain kadre-appkit/src/jvmTest
rtk git commit -m "fix(appkit): own and release CF run-loop resources"
```

---

### Task 3: Complete AppKit lifecycle and clean delegate/IME registries

**Files:**
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitEventLoop.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/KadreAppDelegate.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/KadreWindowDelegate.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitImeTextInputClient.kt`
- Modify: `kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt`
- Modify: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/KadreApplicationTest.kt`
- Modify: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/KadreWindowDelegateTest.kt`
- Create: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/AppKitRegistryLifecycleTest.kt`

**Step 1: Write failing lifecycle/registry tests**

Using fake NSApplication/delegate operations, assert:

- launch emits `resumed`, `newEvents(Init)`, then `canCreateSurfaces`, each once;
- application deactivation/activation emits `suspended`/`resumed` once per transition;
- termination emits `destroySurfaces -> close windows -> suspended`, then releases run-loop/delegates;
- closing a window removes `windows`, window delegate table, and IME client table before native release;
- running a complete fake `runApp` twice leaves every table empty and no callback from run 1 reaches run 2.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-appkit:jvmTest --tests '*KadreApplicationTest' --tests '*KadreWindowDelegateTest' --tests '*AppKitRegistryLifecycleTest'
```

Expected: startup omits callbacks and delegate/observer global tables retain entries.

**Step 3: Centralize lifecycle transitions**

Make `KadreAppDelegate` call loop methods (`didLaunch`, `didBecomeActive`, `willResignActive`, `willTerminate`) instead of invoking handler methods directly. The loop guards duplicate transitions and uses the shared order.

`runApp` retains `appDelegate` and `CFRunLoopOwner`; its `finally` must:

1. request surface destruction if not already done;
2. close remaining windows and unregister delegate/IME objects;
3. close run-loop owner;
4. detach/release application delegate and clear `app.eventLoop`/`sharedApp`;
5. reset `appKitRunning`.

Native callbacks catch, queue, and wake on Kotlin exceptions; the safe loop boundary rethrows with callback context after cleanup.

**Step 4: Make `AppKitWindow.close()` terminal**

Delegate to `AppKitEventLoop.closeWindow(id)` so redraw state and registries are removed before `[NSWindow close]`. `windowWillClose` becomes a native confirmation and cannot emit a duplicate `Destroyed`.

**Step 5: Run the AppKit suite**

```bash
rtk ./gradlew :kadre-appkit:jvmTest
```

Expected: all fake lifecycle/ownership tests pass on any host; native tests are conditionally enabled on macOS.

**Step 6: Commit**

```bash
rtk git add kadre-appkit
rtk git commit -m "fix(appkit): enforce lifecycle and registry cleanup"
```

---

### Task 4: Reuse UIKit windows during foreground surface recreation

**Files:**
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitActiveEventLoop.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/KadreAppDelegate.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UiKitWindow.kt`
- Modify: `samples/hello-touch/src/iosMain/kotlin/org/graphiks/kadre/samples/hellotouch/Main.kt`
- Create: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UIKitLifecycleTest.kt`

**Step 1: Write the failing behavioral test**

Drive delegate-independent loop methods through:

```text
launch -> active -> resign -> background -> foreground -> active
```

The handler calls `createWindow` on both `canCreateSurfaces` invocations. Assert:

- exactly one window ID exists throughout;
- the second call returns the same window object/ID;
- callbacks are `canCreate, resumed, focused+, focused-, suspended, occluded+, destroySurfaces, occluded-, canCreate, resumed, focused+` in the documented order;
- surface destruction occurs once per background cycle, not again on immediate termination;
- a closed window is never reused.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitLifecycleTest'
```

Expected: foreground `canCreateSurfaces` appends a second `UiKitWindow`.

**Step 3: Add an internal recreation session**

`UIKitActiveEventLoop.recreateSurfaces { handler.canCreateSurfaces(this) }` sets a private reuse cursor for the duration of the callback. During that session, `createWindow` returns the next still-open existing window and reapplies mutable attributes; only calls beyond the existing count create a new window. Clear the session in `finally`.

This is internal implementation of the already-approved lifecycle behavior; do not add/change a public method signature.

**Step 4: Make the sample retain/reuse explicitly**

In the iOS hello-touch handler:

```kotlin
window = window ?: eventLoop.createWindow(WindowAttributes(title = "Hello Touch"))
```

Clear it only on `WindowEvent.Destroyed`. This documents the intended application pattern even though the backend also prevents duplication.

**Step 5: Make close unregister immediately**

`UiKitWindow.close()` calls `eventLoop.closeWindow(id)`. Remove from the live list before invalidating scheduler/gesture/IME/drop resources; emit one `Destroyed`; hide/resign the UIWindow. All focus/occlusion/theme dispatch iterates a snapshot of live windows only.

**Step 6: Run tests and commit**

```bash
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitLifecycleTest'
rtk git add kadre-uikit samples/hello-touch/src/iosMain
rtk git commit -m "fix(uikit): reuse windows across surface recreation"
```

---

### Task 5: Make UIKit scheduling demand-driven and safe area physical

**Files:**
- Create: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitLoopState.kt`
- Create: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitScheduler.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitActiveEventLoop.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UIKitEventLoopProxy.kt`
- Modify: `kadre-uikit/src/iosMain/kotlin/org/graphiks/kadre/uikit/UiKitWindow.kt`
- Create: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UIKitSchedulerTest.kt`
- Create: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UIKitSafeAreaTest.kt`

**Step 1: Write failing deterministic scheduler tests**

With fake main-queue/display-link/timer operations and fake epoch clock, assert:

- display link is stopped in `Wait` with no work;
- one redraw starts it and yields one redraw, then stops;
- `Poll` keeps it active; switching to `Wait` stops it;
- three proxy wake cycles each run `newEvents -> queued work -> aboutToWait`;
- `WaitUntil` creates one generation-guarded deadline, an earlier event yields `WaitCancelled(deadline)`, and stale deadline blocks do nothing;
- deadline fire yields `ResumeTimeReached(deadline, now >= deadline)`;
- close cancels the window's pending redraw and no later frame targets it.

Safe-area pure tests must use a named helper:

```kotlin
physicalInset(points = 10.25, scale = 3.0) == 31
```

Specify consistent `roundToInt()` behavior for all four edges and reject non-finite/negative values as zero.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitSchedulerTest' --tests '*UIKitSafeAreaTest'
```

Expected: every window currently runs a perpetual display link, `requestRedraw` is a no-op, and safe area returns points truncated to Int.

**Step 3: Implement one loop-level scheduler**

- Move `CADisplayLink` ownership out of each `UiKitWindow` into `UIKitScheduler`.
- `UiKitWindow.requestRedraw()` queues its ID; coalesce until dispatch.
- Proxy `dispatch_async` calls scheduler `wakeExternal()`; it never directly invokes `handler.newEvents`.
- Use `dispatch_after`/main queue with a monotonically increasing generation. A stale block is harmless; pending-generation state is cancelled on an earlier event or close.
- Each tick follows `newEvents -> live redraws -> aboutToWait -> arm/stop`.

**Step 4: Convert safe area to physical pixels**

Multiply each `UIEdgeInsets` point value by the window's current scale and `roundToInt()`. Apply the approved physical-pixel contract to KDoc in plan 01 and this implementation.

**Step 5: Run tests and commit**

```bash
rtk ./gradlew :kadre-uikit:iosSimulatorArm64Test --tests '*UIKitSchedulerTest' --tests '*UIKitSafeAreaTest'
rtk git add kadre-uikit
rtk git commit -m "fix(uikit): schedule on demand and scale safe area"
```

---

### Task 6: Replace unsupported Native reflection and run real Apple gates

**Files:**
- Replace: `kadre-uikit/src/iosTest/kotlin/org/graphiks/kadre/uikit/UiKitWindowNoOpTest.kt`
- Modify: `kadre-appkit/build.gradle.kts`
- Modify: `kadre-appkit/src/jvmTest/kotlin/org/graphiks/kadre/appkit/AppKitNativeIntegrationTest.kt`
- Create: `scripts/test-appkit-runtime.sh`
- Create: `scripts/test-uikit-simulator.sh`

**Step 1: Replace reflection-only UIKit tests**

Remove all `UiKitWindow::class.members` calls. Cover no-op/result behavior through a small internal `UIKitWindowCapabilities` policy object called by `UiKitWindow`, plus the lifecycle/scheduler tests above. Directly assert returned `WindowRequestResult` values and state, not method presence.

**Step 2: Make AppKit native tests launch correctly**

On macOS only, configure the `jvmTest` process with:

```kotlin
jvmArgs("-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED")
```

Add a native integration handler that opens then closes one window, performs three proxy wakes, requests redraw after idle, exits, and records the strict lifecycle. Run it twice in the same test process and assert delegate/IME/run-loop registries are empty after each run.

**Step 3: Add bounded platform drivers**

`scripts/test-appkit-runtime.sh` checks `uname == Darwin`, then runs `:kadre-appkit:jvmTest` with a 10-minute external timeout.

`scripts/test-uikit-simulator.sh`:

1. selects or creates an available recent iPhone simulator by UDID;
2. boots it and uses `xcrun simctl bootstatus "$UDID" -b` (no fixed sleep);
3. runs `:kadre-uikit:iosSimulatorArm64Test` and the iOS sample tests;
4. shuts down only a simulator it booted;
5. preserves the Gradle exit code.

**Step 4: Run both Apple gates**

```bash
rtk chmod +x scripts/test-appkit-runtime.sh scripts/test-uikit-simulator.sh
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-uikit-simulator.sh
rtk ./gradlew :kadre-uikit:compileKotlinIosX64 :kadre-uikit:compileKotlinIosArm64
```

Expected on macOS: all runtime/simulator tests pass; both retained library targets compile. On non-macOS CI these scripts fail fast with a clear unsupported-host message and are not scheduled.

**Step 5: Commit**

```bash
rtk git add kadre-appkit kadre-uikit scripts/test-appkit-runtime.sh scripts/test-uikit-simulator.sh
rtk git commit -m "test(apple): run behavioral AppKit and UIKit gates"
```
