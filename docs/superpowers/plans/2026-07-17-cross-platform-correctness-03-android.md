# Android Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Make Android surface creation, redraw/wake scheduling, close, and monitor data satisfy the common contracts, with host tests plus a blocking emulator test.

**Architecture:** `KadreActivity` owns the current Android `Surface`; `AndroidEventLoop` can therefore attach it synchronously to a window created inside `canCreateSurfaces`. A pure `AndroidLoopState` owns coalescing/closed-window decisions, while one main-thread scheduler maps that state to `Handler`, `Choreographer`, and deadline callbacks. Emulator tests prove the real `SurfaceHolder` ordering that host reflection tests cannot.

**Tech Stack:** Kotlin Multiplatform Android, AndroidX Activity/ActivityScenario, Android Host Test, Android Device Test, `Handler`, `Choreographer`, API 35 emulator.

## Global Constraints

- Complete `01-contracts` and `02-build-foundation` first.
- Keep the public Kadre API unchanged in this plan.
- Run all Android framework operations on the main Looper.
- No reflection-only test counts as proof of runtime lifecycle behavior.
- Use `rtk` for every shell command and commit after each green task.

---

### Task 1: Add the real-device surface ordering test

**Files:**
- Modify: `kadre-android/build.gradle.kts`
- Create: `kadre-android/src/androidDeviceTest/AndroidManifest.xml`
- Create: `kadre-android/src/androidDeviceTest/kotlin/org/graphiks/kadre/android/SurfaceLifecycleTestActivity.kt`
- Create: `kadre-android/src/androidDeviceTest/kotlin/org/graphiks/kadre/android/AndroidSurfaceLifecycleDeviceTest.kt`

**Step 1: Enable the KMP device test component**

Inside `kotlin { android { ... } }` add:

```kotlin
withDeviceTest {
    instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    execution = "HOST"
}
```

Add to `androidDeviceTest.dependencies`:

```kotlin
implementation(kotlin("test"))
implementation("androidx.test.ext:junit:1.2.1")
implementation("androidx.test:runner:1.6.2")
implementation("androidx.test:core:1.6.1")
```

**Step 2: Register a test-only activity**

Use this test manifest entry:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="org.graphiks.kadre.android.SurfaceLifecycleTestActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" />
    </application>
</manifest>
```

The activity receives an `ApplicationHandler` through a test-only companion factory and clears the factory in `onDestroy`.

**Step 3: Write the failing lifecycle test**

Launch the activity using `ActivityScenario.launch`. In the handler:

```kotlin
override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
    val window = eventLoop.createWindow(WindowAttributes())
    val handle = assertIs<RawWindowHandle.Android>(window.rawWindowHandle)
    assertTrue(handle.surface.isValid)
    result.complete(Unit)
}
```

Wait with a five-second bounded latch/future, close the scenario in `finally`, and rethrow any callback assertion on the instrumentation thread.

**Step 4: Run it to verify the defect**

With an API 35 emulator already booted, run the device-test task discovered in plan 02:

```bash
rtk ./gradlew :kadre-android:connectedAndroidTest
```

Expected before the fix: failure from `rawWindowHandle` because `onSurfaceCreated` currently runs after `canCreateSurfaces`. If AGP's task report in plan 02 gives another generated device-test task name, substitute that exact name here and in CI.

**Step 5: Commit the red test**

```bash
rtk git add kadre-android/build.gradle.kts kadre-android/src/androidDeviceTest
rtk git commit -m "test(android): reproduce invalid surface callback ordering"
```

---

### Task 2: Make the surface valid during `canCreateSurfaces`

**Files:**
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidEventLoop.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/KadreActivity.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidWindow.kt`
- Modify: `kadre-android/src/androidDeviceTest/kotlin/org/graphiks/kadre/android/AndroidSurfaceLifecycleDeviceTest.kt`

**Step 1: Store surface availability independently of window creation**

Add to `AndroidEventLoop`:

```kotlin
private var currentSurface: Surface? = null

internal fun onSurfaceCreated(surface: Surface) {
    currentSurface = surface
    pendingWindow?.onSurfaceAvailable(surface)
}

internal fun onSurfaceDestroyed() {
    pendingWindow?.onSurfaceReleased()
    currentSurface = null
}
```

In `createWindow`, attach `currentSurface` before returning:

```kotlin
return AndroidWindow(kadreActivity.surfaceView, this, kadreActivity).also { window ->
    currentSurface?.let(window::onSurfaceAvailable)
    pendingWindow = window
}
```

**Step 2: Correct the callback order**

Change `surfaceCreated` to:

```kotlin
eventLoop.onSurfaceCreated(holder.surface)
handler.canCreateSurfaces(eventLoop)
eventLoop.pendingWindow?.let(eventLoop::scheduleFrameIfNeeded)
```

Keep `surfaceDestroyed` as `destroySurfaces` first, then native-handle invalidation.

**Step 3: Add recreation coverage**

Extend the device test to call `scenario.recreate()`. Record this exact subsequence twice:

```text
canCreateSurfaces -> readable rawWindowHandle -> destroySurfaces
```

Assert that a handle from the destroyed surface is not returned after recreation and that the new handle is valid.

**Step 4: Run the focused test**

```bash
rtk ./gradlew :kadre-android:connectedAndroidTest
```

Expected: lifecycle test passes twice (initial creation and recreation).

**Step 5: Commit**

```bash
rtk git add kadre-android/src/androidMain kadre-android/src/androidDeviceTest
rtk git commit -m "fix(android): expose surface before creation callback"
```

---

### Task 3: Specify redraw, wake-up, deadline, and close state without Android runtime dependencies

**Files:**
- Create: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidLoopState.kt`
- Create: `kadre-android/src/androidHostTest/kotlin/org/graphiks/kadre/android/AndroidLoopStateTest.kt`

**Step 1: Write failing host tests**

Cover:

1. three `wakeUp -> takeStartCause` cycles;
2. ten redraw requests coalesce to one window ID;
3. consuming redraw permits a later redraw;
4. an event before `WaitUntil` produces `WaitCancelled(requestedResume)`;
5. the deadline produces `ResumeTimeReached(requestedResume, now)` only at/after the epoch-millisecond deadline;
6. closing removes queued redraw/wake work and all later enqueue calls return false.

Use a fake clock passed as `nowMillis: () -> Long`; never sleep.

**Step 2: Verify tests fail because the state component does not exist**

```bash
rtk ./gradlew :kadre-android:testAndroidHostTest --tests '*AndroidLoopStateTest'
```

Expected: compilation failure for missing `AndroidLoopState`.

**Step 3: Implement the minimal pure state machine**

The internal API should be explicit and synchronous:

```kotlin
internal class AndroidLoopState(
    private val nowMillis: () -> Long,
) {
    fun register(windowId: WindowId)
    fun requestRedraw(windowId: WindowId): Boolean
    fun takeRedraws(): List<WindowId>
    fun wakeUp(): Boolean
    fun takeStartCause(controlFlow: ControlFlow): StartCause?
    fun close(windowId: WindowId): Boolean
    fun isOpen(windowId: WindowId): Boolean
}
```

Keep pending redraws in insertion order, use one pending wake bit that is cleared on consume, and derive all timestamps from `System.currentTimeMillis` only at the adapter boundary.

**Step 4: Run the host tests**

```bash
rtk ./gradlew :kadre-android:testAndroidHostTest --tests '*AndroidLoopStateTest'
```

Expected: all state-machine tests pass without emulator or wall-clock delay.

**Step 5: Commit**

```bash
rtk git add kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidLoopState.kt kadre-android/src/androidHostTest
rtk git commit -m "test(android): define deterministic loop state"
```

---

### Task 4: Connect the state machine to one main-thread scheduler

**Files:**
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidEventLoop.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidWindow.kt`
- Create: `kadre-android/src/androidDeviceTest/kotlin/org/graphiks/kadre/android/AndroidSchedulerDeviceTest.kt`

**Step 1: Write failing device behavior tests**

In the real activity test, assert with bounded latches that:

- `aboutToWait -> requestRedraw` yields exactly one `newEvents`, one `RedrawRequested`, then `aboutToWait`;
- ten redraw calls before the frame yield one redraw event;
- each of three background-thread `proxy.wakeUp()` calls yields a new `WaitCancelled` iteration;
- a 100 ms `WaitUntil` yields `ResumeTimeReached` with `start >= requestedResume`;
- an input/redraw wake before a one-second deadline yields `WaitCancelled`, never `ResumeTimeReached` for that deadline.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-android:connectedAndroidTest
```

Expected: redraw after idle or proxy wake assertions time out because both are currently unscheduled/no-op.

**Step 3: Implement a single scheduler**

Use one `Handler(Looper.getMainLooper())`. Every public/internal entry point posts to it when called off-main. The adapter performs one iteration in this order:

```kotlin
handler.newEvents(this, cause)
state.takeRedraws().forEach { id ->
    openWindow(id)?.let { handler.windowEvent(this, id, WindowEvent.RedrawRequested) }
}
handler.aboutToWait(this)
armNextWait()
```

- `AndroidWindow.requestRedraw()` calls `eventLoop.requestRedraw(id)`; a newly queued redraw schedules a `Choreographer` callback.
- `createProxy().wakeUp()` calls `mainHandler.post { signalWake() }`; it never touches `Activity` or `Choreographer` from the caller's thread.
- `WaitUntil` uses `postAtTime`/`postDelayed` based on epoch milliseconds and holds a unique generation token so stale timers cannot fire.
- `Wait` arms no timer; `Poll` schedules the next frame; pending work always wakes idle mode.
- Remove the current `AndroidWindow.needsRedraw` bit once `AndroidLoopState` owns coalescing.

**Step 4: Run host and device tests**

```bash
rtk ./gradlew :kadre-android:testAndroidHostTest
rtk ./gradlew :kadre-android:connectedAndroidTest
```

Expected: deterministic host state tests and all main-Looper scheduling tests pass.

**Step 5: Commit**

```bash
rtk git add kadre-android/src/androidMain kadre-android/src/androidDeviceTest
rtk git commit -m "fix(android): schedule redraw wake and deadlines"
```

---

### Task 5: Make close terminal and monitor refresh rate correct

**Files:**
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidEventLoop.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/AndroidWindow.kt`
- Modify: `kadre-android/src/androidMain/kotlin/org/graphiks/kadre/android/KadreActivity.kt`
- Create: `kadre-android/src/androidHostTest/kotlin/org/graphiks/kadre/android/AndroidVideoModeTest.kt`
- Modify: `kadre-android/src/androidDeviceTest/kotlin/org/graphiks/kadre/android/AndroidSchedulerDeviceTest.kt`

**Step 1: Add failing tests**

- Host-test a pure `refreshRateMillihertz(refreshRateHz: Float)` helper: 60.0 Hz -> 60,000 mHz; 59.94 Hz -> 59,940 mHz; non-finite/non-positive -> null.
- Device-test `window.close(); window.close(); window.requestRedraw()` and assert exactly one `Destroyed`, an invalid raw handle, no later redraw/focus/occlusion event, and an empty event-loop registry.

**Step 2: Verify failure**

```bash
rtk ./gradlew :kadre-android:testAndroidHostTest --tests '*AndroidVideoModeTest'
rtk ./gradlew :kadre-android:connectedAndroidTest
```

Expected: refresh helper absent and `close()` still a no-op.

**Step 3: Implement close ownership**

`AndroidWindow.close()` delegates once to `AndroidEventLoop.closeWindow(this)`. That method, on the main Looper:

1. removes the ID from `AndroidLoopState` and the registry/pending reference;
2. cancels queued redraw/timer/frame work if no windows remain;
3. releases the surface reference;
4. dispatches exactly one `WindowEvent.Destroyed` while retaining only the local window ID;
5. optionally finishes the single-window Activity after dispatch.

All `KadreActivity` callbacks must resolve an open window through the event loop rather than retaining a closed `pendingWindow`. `onDestroy` must not emit a second `Destroyed`.

**Step 4: Correct `VideoMode` construction**

Use named arguments and the display refresh rate:

```kotlin
VideoMode(
    size = PhysicalSize(dm.widthPixels, dm.heightPixels),
    bitDepth = null,
    refreshRateMilliHz = refreshRateMillihertz(activity.display?.refreshRate ?: 0f),
)
```

Apply the same construction to `AndroidWindow.currentMonitor`; never derive refresh rate from `xdpi`.

**Step 5: Run the Android gate**

```bash
rtk ./gradlew :kadre-android:testAndroidHostTest :kadre-android:connectedAndroidTest :samples:hello-window-android:assembleDebug
```

Expected: all exit 0; device logs contain no event for the closed ID after `Destroyed`.

**Step 6: Commit**

```bash
rtk git add kadre-android
rtk git commit -m "fix(android): make close terminal and report refresh rate"
```
