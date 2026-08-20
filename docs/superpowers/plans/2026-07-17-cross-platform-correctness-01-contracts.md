# Event-loop Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Kadre's lifecycle, iteration, redraw, wake-up, close, and geometry contracts executable and reusable by every backend test suite.

**Architecture:** `kadre-core` remains the public contract. `kadre-test` adds observation vocabulary and assertion functions without exposing test hooks in production APIs; backend tests implement small drivers around their internal loops.

**Tech Stack:** Kotlin common code, `kotlin.test`, existing `ScriptedEventLoop`, Gradle KMP tests.

## Global Constraints

- Windows is out of scope; do not edit Win32 sources or tests.
- `safeArea` is defined in physical pixels.
- `WaitUntil.instant` and `StartCause` timestamps are Unix epoch milliseconds.
- No new public production type is introduced by this plan.
- Every shell command is prefixed with `rtk`.
- Follow TDD and commit each task independently.

---

### Task 1: Add reusable callback trace assertions

**Files:**
- Create: `kadre-test/src/commonMain/kotlin/org/graphiks/kadre/test/EventLoopConformance.kt`
- Create: `kadre-test/src/commonTest/kotlin/org/graphiks/kadre/test/EventLoopConformanceTest.kt`

**Interfaces:**
- Consumes: `ApplicationHandler`, `ActiveEventLoop`, `StartCause`, `WindowEvent`.
- Produces: `ObservedCallback`, `RecordingApplicationHandler`, `assertIterationOrder(trace)`, and `assertNoCallbacksAfter(trace, marker)`.

- [ ] **Step 1: Write the failing conformance-helper tests**

```kotlin
package org.graphiks.kadre.test

import kotlin.test.Test
import kotlin.test.assertFailsWith

class EventLoopConformanceTest {
    @Test
    fun validIterationIsAccepted() {
        assertIterationOrder(
            listOf(
                ObservedCallback.NewEvents,
                ObservedCallback.WindowEvent,
                ObservedCallback.AboutToWait,
            )
        )
    }

    @Test
    fun eventBeforeNewEventsIsRejected() {
        assertFailsWith<AssertionError> {
            assertIterationOrder(
                listOf(
                    ObservedCallback.WindowEvent,
                    ObservedCallback.NewEvents,
                    ObservedCallback.AboutToWait,
                )
            )
        }
    }

    @Test
    fun callbackAfterClosedMarkerIsRejected() {
        assertFailsWith<AssertionError> {
            assertNoCallbacksAfter(
                listOf(ObservedCallback.Closed, ObservedCallback.WindowEvent),
                ObservedCallback.Closed,
            )
        }
    }
}
```

- [ ] **Step 2: Run the test and observe the missing symbols**

Run: `rtk ./gradlew :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: compilation fails because `ObservedCallback`, `assertIterationOrder`, and `assertNoCallbacksAfter` do not exist.

- [ ] **Step 3: Implement the trace vocabulary and assertions**

```kotlin
package org.graphiks.kadre.test

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.DeviceEvent
import org.graphiks.kadre.core.DeviceId
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId

enum class ObservedCallback {
    CanCreateSurfaces,
    Resumed,
    NewEvents,
    WindowEvent,
    RedrawRequested,
    Destroyed,
    DeviceEvent,
    AboutToWait,
    DestroySurfaces,
    Suspended,
    Closed,
}

class RecordingApplicationHandler(
    private val onCanCreateSurfaces: (ActiveEventLoop) -> Unit = {},
) : ApplicationHandler {
    val trace = mutableListOf<ObservedCallback>()
    val startCauses = mutableListOf<StartCause>()
    val windowEvents = mutableListOf<Pair<WindowId, WindowEvent>>()

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        trace += ObservedCallback.CanCreateSurfaces
        onCanCreateSurfaces(eventLoop)
    }

    override fun resumed(eventLoop: ActiveEventLoop) { trace += ObservedCallback.Resumed }
    override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
        trace += ObservedCallback.NewEvents
        startCauses += startCause
    }
    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
        trace += when (event) {
            WindowEvent.RedrawRequested -> ObservedCallback.RedrawRequested
            WindowEvent.Destroyed -> ObservedCallback.Destroyed
            else -> ObservedCallback.WindowEvent
        }
        windowEvents += windowId to event
    }
    override fun deviceEvent(eventLoop: ActiveEventLoop, deviceId: DeviceId, event: DeviceEvent) {
        trace += ObservedCallback.DeviceEvent
    }
    override fun aboutToWait(eventLoop: ActiveEventLoop) { trace += ObservedCallback.AboutToWait }
    override fun destroySurfaces(eventLoop: ActiveEventLoop) { trace += ObservedCallback.DestroySurfaces }
    override fun suspended(eventLoop: ActiveEventLoop) { trace += ObservedCallback.Suspended }
    fun markClosed() { trace += ObservedCallback.Closed }
}

fun assertIterationOrder(trace: List<ObservedCallback>) {
    val newEvents = trace.indexOf(ObservedCallback.NewEvents)
    val aboutToWait = trace.lastIndexOf(ObservedCallback.AboutToWait)
    if (newEvents < 0 || aboutToWait < 0 || newEvents >= aboutToWait) {
        throw AssertionError("Expected NewEvents before AboutToWait, got $trace")
    }
    val dispatch = trace.indexOfFirst {
        it == ObservedCallback.WindowEvent ||
            it == ObservedCallback.RedrawRequested ||
            it == ObservedCallback.Destroyed ||
            it == ObservedCallback.DeviceEvent
    }
    if (dispatch >= 0 && dispatch !in (newEvents + 1) until aboutToWait) {
        throw AssertionError("Expected dispatch between NewEvents and AboutToWait, got $trace")
    }
}

fun assertNoCallbacksAfter(trace: List<ObservedCallback>, marker: ObservedCallback) {
    val markerIndex = trace.indexOf(marker)
    if (markerIndex < 0 || markerIndex != trace.lastIndex) {
        throw AssertionError("Expected $marker to be the final callback, got $trace")
    }
}
```

- [ ] **Step 4: Run all `kadre-test` targets**

Run: `rtk ./gradlew :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: `BUILD SUCCESSFUL` and all three new tests pass.

- [ ] **Step 5: Commit the helper**

```bash
rtk git add kadre-test/src/commonMain/kotlin/org/graphiks/kadre/test/EventLoopConformance.kt kadre-test/src/commonTest/kotlin/org/graphiks/kadre/test/EventLoopConformanceTest.kt
rtk git commit -m "test: add shared event-loop contract assertions"
```

### Task 2: Add wake-up, redraw, and close driver contracts

**Files:**
- Modify: `kadre-test/src/commonMain/kotlin/org/graphiks/kadre/test/EventLoopConformance.kt`
- Modify: `kadre-test/src/commonTest/kotlin/org/graphiks/kadre/test/EventLoopConformanceTest.kt`

**Interfaces:**
- Produces: `EventLoopConformanceDriver`, `assertWakeUpRearms(factory)`, `assertRedrawAfterIdle(factory)`, `assertCloseIsTerminal(factory)`.
- Driver implementers supply deterministic `start`, `wakeUp`, `requestRedraw`, `waitForIdle`, `closeWindow`, and `shutdown` operations.

- [ ] **Step 1: Add failing tests with a deliberately broken driver**

```kotlin
private class FakeDriver(
    private val oneShotWake: Boolean = false,
    private val dispatchAfterClose: Boolean = false,
) : EventLoopConformanceDriver {
    override val trace = mutableListOf<ObservedCallback>()
    private var wakes = 0
    private var pendingWake = false
    private var pendingRedraw = false
    private var closed = false

    override fun start() = Unit
    override fun wakeUp() {
        if ((!closed || dispatchAfterClose) && (!oneShotWake || wakes++ == 0)) pendingWake = true
    }
    override fun requestRedraw() {
        if (!closed || dispatchAfterClose) pendingRedraw = true
    }
    override fun waitForIdle() {
        if (!pendingWake && !pendingRedraw) return
        trace += ObservedCallback.NewEvents
        if (pendingRedraw) trace += ObservedCallback.RedrawRequested
        trace += ObservedCallback.AboutToWait
        pendingWake = false
        pendingRedraw = false
    }
    override fun closeWindow() {
        if (closed) return
        closed = true
        pendingWake = false
        pendingRedraw = false
        trace += ObservedCallback.Destroyed
        trace += ObservedCallback.Closed
    }
    override fun shutdown() = Unit
}

@Test
fun oneShotWakeDriverFailsConformance() {
    assertFailsWith<AssertionError> { assertWakeUpRearms { FakeDriver(oneShotWake = true) } }
}

@Test
fun reusableWakeDriverPassesConformance() {
    assertWakeUpRearms { FakeDriver() }
}

@Test
fun redrawAfterIdleCoalesces() {
    assertRedrawAfterIdle { FakeDriver() }
}

@Test
fun terminalCloseDriverPassesConformance() {
    assertCloseIsTerminal { FakeDriver() }
}

@Test
fun callbackAfterCloseFailsConformance() {
    assertFailsWith<AssertionError> {
        assertCloseIsTerminal { FakeDriver(dispatchAfterClose = true) }
    }
}
```

- [ ] **Step 2: Run the focused test and observe missing driver APIs**

Run: `rtk ./gradlew :kadre-test:jvmTest --tests '*EventLoopConformanceTest*' --no-daemon --stacktrace --console=plain`

Expected: compilation fails on `EventLoopConformanceDriver` and `assertWakeUpRearms`.

- [ ] **Step 3: Implement the driver interface and assertions**

```kotlin
interface EventLoopConformanceDriver {
    val trace: MutableList<ObservedCallback>
    fun start()
    fun wakeUp()
    fun requestRedraw()
    fun waitForIdle()
    fun closeWindow()
    fun shutdown()
}

fun assertWakeUpRearms(factory: () -> EventLoopConformanceDriver) {
    val driver = factory()
    try {
        driver.start()
        repeat(3) { cycle ->
            val before = driver.trace.count { it == ObservedCallback.NewEvents }
            driver.wakeUp()
            driver.waitForIdle()
            val after = driver.trace.count { it == ObservedCallback.NewEvents }
            if (after != before + 1) throw AssertionError("Wake cycle $cycle was not dispatched: ${driver.trace}")
            val iterationStart = driver.trace.indexOfLast { it == ObservedCallback.NewEvents }
            val iteration = driver.trace.drop(iterationStart)
            assertIterationOrder(iteration)
        }
    } finally {
        driver.shutdown()
    }
}

fun assertRedrawAfterIdle(factory: () -> EventLoopConformanceDriver) {
    val driver = factory()
    try {
        driver.start()
        driver.waitForIdle()
        val before = driver.trace.size
        repeat(10) { driver.requestRedraw() }
        driver.waitForIdle()
        val iteration = driver.trace.drop(before)
        if (iteration.count { it == ObservedCallback.RedrawRequested } != 1) {
            throw AssertionError("Expected one coalesced redraw, got $iteration")
        }
        assertIterationOrder(iteration)
    } finally {
        driver.shutdown()
    }
}

fun assertCloseIsTerminal(factory: () -> EventLoopConformanceDriver) {
    val driver = factory()
    try {
        driver.start()
        driver.closeWindow()
        driver.requestRedraw()
        driver.wakeUp()
        driver.waitForIdle()
        assertNoCallbacksAfter(driver.trace, ObservedCallback.Closed)
    } finally {
        driver.shutdown()
    }
}
```

- [ ] **Step 4: Run the contract tests**

Run: `rtk ./gradlew :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: the broken one-shot driver is rejected and the reusable driver passes.

- [ ] **Step 5: Commit the driver contract**

```bash
rtk git add kadre-test/src/commonMain/kotlin/org/graphiks/kadre/test/EventLoopConformance.kt kadre-test/src/commonTest/kotlin/org/graphiks/kadre/test/EventLoopConformanceTest.kt
rtk git commit -m "test: define redraw wake and close conformance drivers"
```

### Task 3: Tighten public contract documentation

**Files:**
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ApplicationHandler.kt`
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/EventLoopProxy.kt`
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Types.kt`
- Modify: `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt`
- Test: `kadre-core/src/commonTest/kotlin/org/graphiks/kadre/core/ApplicationHandlerTest.kt`

**Interfaces:**
- Consumes: existing public signatures unchanged.
- Produces: exact ordering, timestamp, wake-up, close, and physical-pixel documentation.

- [ ] **Step 1: Add a documentation contract test**

Add to `ApplicationHandlerTest.kt`:

```kotlin
@Test
fun controlFlowWaitUntilUsesUnixEpochMilliseconds() {
    val target = 1_700_000_000_000L
    val flow = ControlFlow.WaitUntil(target)
    assertEquals(target, flow.instant)
    val cause = StartCause.ResumeTimeReached(target, target + 5)
    assertEquals(target, cause.requestedResume)
    assertEquals(target + 5, cause.start)
}
```

- [ ] **Step 2: Run the focused core test**

Run: `rtk ./gradlew :kadre-core:allTests --no-daemon --stacktrace --console=plain`

Expected: existing behavior passes; this establishes the unchanged timestamp representation before documentation edits.

- [ ] **Step 3: Replace ambiguous KDoc with exact contract text**

Use these exact statements in the relevant declarations:

```kotlin
/**
 * Called once at the start of an iteration, before any window or device event.
 * [ApplicationHandler.aboutToWait] is the final callback of that iteration.
 */
fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause): Unit = Unit

/**
 * Wakes a waiting loop. Calls are coalesced only until the loop consumes the wake-up;
 * subsequent calls must wake subsequent waits. Safe from any thread.
 */
fun wakeUp()

/**
 * Insets of the unobstructed renderable area, in physical pixels inside [surfaceSize].
 */
val safeArea: Insets<Int> get() = Insets(0, 0, 0, 0)
```

Also document that `close()` invalidates handles and forbids later events, and that `WaitUntil.instant` and both `ResumeTimeReached` values are Unix epoch milliseconds.

- [ ] **Step 4: Run core and test-helper suites**

Run: `rtk ./gradlew :kadre-core:allTests :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the contract documentation**

```bash
rtk git add kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core kadre-core/src/commonTest/kotlin/org/graphiks/kadre/core/ApplicationHandlerTest.kt
rtk git commit -m "docs: define event-loop lifecycle contracts"
```

### Task 4: Run the plan gate

**Files:**
- Verify only; no source changes expected.

**Interfaces:**
- Produces: plan-01 green gate for every later plan.

- [ ] **Step 1: Run all contract targets**

Run: `rtk ./gradlew :kadre-core:allTests :kadre-test:allTests --no-daemon --stacktrace --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check the worktree**

Run: `rtk git status --short`

Expected: no output.
