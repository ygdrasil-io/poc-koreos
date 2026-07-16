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
