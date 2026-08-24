package org.graphiks.kadre.web

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.test.RecordingApplicationHandler
import org.graphiks.kadre.test.assertIterationOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class WebEventLoopTest {

    @Test
    fun `startup records a complete shared iteration`() {
        val loop = TestWebEventLoop(FakeSchedulingApi())
        val handler = RecordingApplicationHandler()

        loop.runApp(handler)

        assertIterationOrder(handler.trace)
    }

    @Test
    fun `startup follows the exact lifecycle order and Wait remains idle`() {
        val api = FakeSchedulingApi()
        val loop = TestWebEventLoop(api)
        val handler = RecordingHandler()

        loop.runApp(handler)

        assertEquals(
            listOf("resumed", "new:Init", "canCreateSurfaces", "aboutToWait"),
            handler.trace,
        )
        assertEquals(emptyList(), api.operations)
        assertEquals(emptySet(), api.activeRafIds)
        assertEquals(emptySet(), api.activeTimeoutIds)
    }

    @Test
    fun `Wait owns no browser id until a proxy wake`() {
        val api = FakeSchedulingApi()
        val loop = TestWebEventLoop(api)
        loop.runApp(RecordingHandler())

        assertEquals(emptyList(), api.operations)

        loop.createProxy().wakeUp()

        assertEquals(listOf("requestAnimationFrame(1)"), api.operations)
        assertEquals(setOf(1), api.activeRafIds)
        assertEquals(emptySet(), api.activeTimeoutIds)
    }

    @Test
    fun `event before WaitUntil deadline cancels timeout and dispatches after exact cause`() {
        val api = FakeSchedulingApi(epochNowMillis = 9_000L)
        val bridge = RecordingBridge()
        val loop = TestWebEventLoop(api, listOf(bridge))
        loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val handler = RecordingHandler()
        loop.setControlFlow(ControlFlow.WaitUntil(10_000L))

        loop.runApp(handler)
        bridge.emit(WebWindowEvent.Focused(true))

        assertEquals(
            listOf(
                "setTimeout(1,1000)",
                "clearTimeout(1)",
                "requestAnimationFrame(2)",
            ),
            api.operations,
        )
        assertEquals(setOf(2), api.activeRafIds)
        assertEquals(emptySet(), api.activeTimeoutIds)

        api.fireAnimationFrame(2)

        assertEquals(
            listOf(
                "new:Init",
                "new:WaitCancelled(10000)",
                "event",
            ),
            handler.trace.filter { it.startsWith("new:") || it == "event" },
        )
    }

    @Test
    fun `deadline samples epoch time before RAF and preserves it`() {
        val api = FakeSchedulingApi(epochNowMillis = 9_000L)
        val loop = TestWebEventLoop(api)
        val handler = RecordingHandler()
        loop.setControlFlow(ControlFlow.WaitUntil(10_000L))
        loop.runApp(handler)

        assertEquals(listOf("setTimeout(1,1000)"), api.operations)

        api.epochNowMillis = 10_005L
        api.fireTimeout(1)
        api.epochNowMillis = 88_888L

        assertEquals(setOf(2), api.activeRafIds)
        api.fireAnimationFrame(2)

        assertEquals(
            listOf(StartCause.Init, StartCause.ResumeTimeReached(10_000L, 10_005L)),
            handler.startCauses.take(2),
        )
    }

    @Test
    fun `WaitUntil re-arms a premature timeout without delivering an iteration`() {
        val api = FakeSchedulingApi(epochNowMillis = 9_000L)
        val delivered = mutableListOf<StartCause>()
        val scheduler = BrowserScheduler(api, delivered::add)

        scheduler.arm(ControlFlow.WaitUntil(10_000L))
        api.epochNowMillis = 9_500L
        api.fireTimeout(1)

        assertEquals(emptySet(), api.activeRafIds)
        assertEquals(setOf(2), api.activeTimeoutIds)
        assertEquals(emptyList<StartCause>(), delivered)
        assertEquals(
            listOf("setTimeout(1,1000)", "setTimeout(2,500)"),
            api.operations,
        )

        api.epochNowMillis = 10_000L
        api.fireTimeout(2)

        assertEquals(setOf(3), api.activeRafIds)
        api.fireAnimationFrame(3)
        assertEquals(
            listOf<StartCause>(StartCause.ResumeTimeReached(10_000L, 10_000L)),
            delivered,
        )
    }

    @Test
    fun `legacy tick timestamp is accepted but cannot influence epoch start`() {
        val api = FakeSchedulingApi(epochNowMillis = 10_005L)
        val loop = TestWebEventLoop(api)
        val handler = RecordingHandler()
        loop.setControlFlow(ControlFlow.WaitUntil(10_000L))

        loop.pump(handler, legacyRafTimestamp = 42.0)

        assertEquals(
            listOf<StartCause>(StartCause.ResumeTimeReached(10_000L, 10_005L)),
            handler.startCauses,
        )
    }

    @Test
    fun `rearm cancels prior ids and stale generations cannot disturb newer ownership`() {
        val api = FakeSchedulingApi(epochNowMillis = 9_000L)
        val delivered = mutableListOf<StartCause>()
        val scheduler = BrowserScheduler(api, delivered::add)

        scheduler.arm(ControlFlow.Poll)
        scheduler.arm(ControlFlow.WaitUntil(10_000L))
        scheduler.arm(ControlFlow.Poll)

        assertEquals(
            listOf(
                "requestAnimationFrame(1)",
                "cancelAnimationFrame(1)",
                "setTimeout(2,1000)",
                "clearTimeout(2)",
                "requestAnimationFrame(3)",
            ),
            api.operations,
        )
        assertEquals(setOf(3), api.activeRafIds)

        val operationsBeforeStaleCallbacks = api.operations.toList()
        api.fireAnimationFrame(1)
        api.fireTimeout(2)

        assertEquals(emptyList<StartCause>(), delivered)
        assertEquals(operationsBeforeStaleCallbacks, api.operations)
        assertEquals(setOf(3), api.activeRafIds)

        api.fireAnimationFrame(3)
        assertEquals(listOf<StartCause>(StartCause.Poll), delivered)
    }

    @Test
    fun `WaitUntil delay clamps without integer overflow`() {
        val api = FakeSchedulingApi(epochNowMillis = 0L)
        val scheduler = BrowserScheduler(api) { }

        scheduler.arm(ControlFlow.WaitUntil(Long.MAX_VALUE))
        scheduler.arm(ControlFlow.WaitUntil(-1L))

        assertEquals(
            listOf(
                "setTimeout(1,${Int.MAX_VALUE})",
                "clearTimeout(1)",
                "setTimeout(2,0)",
            ),
            api.operations,
        )
    }

    @Test
    fun `deadline cancellation reports the deadline actually owned`() {
        val api = FakeSchedulingApi(epochNowMillis = 9_000L)
        val delivered = mutableListOf<StartCause>()
        val scheduler = BrowserScheduler(api, delivered::add)
        scheduler.arm(ControlFlow.WaitUntil(10_000L))

        scheduler.signalEvent(ControlFlow.Wait)
        api.fireAnimationFrame(2)

        assertEquals(
            listOf("setTimeout(1,1000)", "clearTimeout(1)", "requestAnimationFrame(2)"),
            api.operations,
        )
        assertEquals(listOf<StartCause>(StartCause.WaitCancelled(10_000L)), delivered)
    }

    @Test
    fun `three Wait proxy cycles own distinct RAF ids and exact causes`() {
        val api = FakeSchedulingApi()
        val loop = TestWebEventLoop(api)
        val handler = RecordingHandler()
        val proxy = loop.createProxy()
        loop.runApp(handler)

        repeat(3) { index ->
            proxy.wakeUp()
            val id = index + 1
            assertEquals(setOf(id), api.activeRafIds)
            api.fireAnimationFrame(id)
            assertEquals(emptySet(), api.activeRafIds)
        }

        assertEquals(
            listOf(
                StartCause.WaitCancelled(),
                StartCause.WaitCancelled(),
                StartCause.WaitCancelled(),
            ),
            handler.startCauses.drop(1),
        )
        assertEquals(
            listOf(
                "requestAnimationFrame(1)",
                "requestAnimationFrame(2)",
                "requestAnimationFrame(3)",
            ),
            api.operations,
        )
    }

    @Test
    fun `proxy wake requested reentrantly is delivered by the next iteration`() {
        val api = FakeSchedulingApi()
        val bridge = RecordingBridge()
        val loop = TestWebEventLoop(api, listOf(bridge))
        loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val proxy = loop.createProxy()
        var wakeRequested = false
        val handler = RecordingHandler(
            onWindowEvent = { _, _, event ->
                if (!wakeRequested && event == WindowEvent.Focused(true)) {
                    wakeRequested = true
                    proxy.wakeUp()
                }
            },
        )
        loop.runApp(handler)

        bridge.emit(WebWindowEvent.Focused(true))
        api.fireAnimationFrame(1)

        assertEquals(setOf(2), api.activeRafIds)
        api.fireAnimationFrame(2)
        assertEquals(
            listOf<StartCause>(
                StartCause.WaitCancelled(),
                StartCause.WaitCancelled(),
            ),
            handler.startCauses.drop(1),
        )
        assertEquals(
            listOf("requestAnimationFrame(1)", "requestAnimationFrame(2)"),
            api.operations,
        )
    }

    @Test
    fun `redraw coalesces before consumption and can be requested again afterwards`() {
        val api = FakeSchedulingApi()
        val bridge = RecordingBridge()
        val loop = TestWebEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val handler = RecordingHandler()
        loop.runApp(handler)

        window.requestRedraw()
        window.requestRedraw()

        assertEquals(listOf("requestAnimationFrame(1)"), api.operations)
        assertEquals(setOf(1), api.activeRafIds)
        api.fireAnimationFrame(1)
        assertEquals(1, handler.windowEvents.count { it.second == WindowEvent.RedrawRequested })

        window.requestRedraw()
        assertEquals(setOf(2), api.activeRafIds)
        api.fireAnimationFrame(2)

        assertEquals(2, handler.windowEvents.count { it.second == WindowEvent.RedrawRequested })
        assertEquals(
            listOf("requestAnimationFrame(1)", "requestAnimationFrame(2)"),
            api.operations,
        )
    }

    @Test
    fun `redraw remains coalesced while its snapshot entry awaits consumption`() {
        val api = FakeSchedulingApi()
        val bridge = RecordingBridge()
        val loop = TestWebEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        var requestedBeforeRedrawConsumption = false
        val handler = RecordingHandler(
            onWindowEvent = { _, _, event ->
                if (!requestedBeforeRedrawConsumption && event == WindowEvent.Focused(true)) {
                    requestedBeforeRedrawConsumption = true
                    window.requestRedraw()
                    window.requestRedraw()
                }
            },
        )
        loop.runApp(handler)

        bridge.emit(WebWindowEvent.Focused(true))
        window.requestRedraw()
        api.fireAnimationFrame(1)

        assertEquals(1, handler.windowEvents.count { it.second == WindowEvent.RedrawRequested })
        assertEquals(emptySet(), api.activeRafIds)
        assertEquals(listOf("requestAnimationFrame(1)"), api.operations)
    }

    @Test
    fun `Poll owns one RAF and rearms only after each consumed callback`() {
        val api = FakeSchedulingApi()
        val loop = TestWebEventLoop(api)
        val handler = RecordingHandler()
        loop.setControlFlow(ControlFlow.Poll)
        loop.runApp(handler)

        repeat(3) { index ->
            val id = index + 1
            assertEquals(setOf(id), api.activeRafIds)
            api.fireAnimationFrame(id)
        }

        assertEquals(listOf(StartCause.Poll, StartCause.Poll, StartCause.Poll), handler.startCauses.drop(1))
        assertEquals(setOf(4), api.activeRafIds)
        assertEquals(
            listOf(
                "requestAnimationFrame(1)",
                "requestAnimationFrame(2)",
                "requestAnimationFrame(3)",
                "requestAnimationFrame(4)",
            ),
            api.operations,
        )
    }

    @Test
    fun `exit cancels outstanding RAF and timeout and stale callbacks stay silent`() {
        val pollApi = FakeSchedulingApi()
        val pollLoop = TestWebEventLoop(pollApi)
        val pollHandler = RecordingHandler()
        pollLoop.setControlFlow(ControlFlow.Poll)
        pollLoop.runApp(pollHandler)
        val pollTraceBeforeExit = pollHandler.trace.toList()

        pollLoop.exit()
        assertEquals(
            listOf("requestAnimationFrame(1)", "cancelAnimationFrame(1)"),
            pollApi.operations,
        )
        pollApi.fireAnimationFrame(1)
        assertEquals(pollTraceBeforeExit, pollHandler.trace)
        assertEquals(emptySet(), pollApi.activeRafIds)
        assertEquals(emptySet(), pollApi.activeTimeoutIds)

        val timeoutApi = FakeSchedulingApi(epochNowMillis = 9_000L)
        val timeoutLoop = TestWebEventLoop(timeoutApi)
        val timeoutHandler = RecordingHandler()
        timeoutLoop.setControlFlow(ControlFlow.WaitUntil(10_000L))
        timeoutLoop.runApp(timeoutHandler)
        val timeoutTraceBeforeExit = timeoutHandler.trace.toList()

        timeoutLoop.exit()
        assertEquals(
            listOf("setTimeout(1,1000)", "clearTimeout(1)"),
            timeoutApi.operations,
        )
        timeoutApi.fireTimeout(1)
        assertEquals(timeoutTraceBeforeExit, timeoutHandler.trace)
        assertEquals(emptySet(), timeoutApi.activeRafIds)
        assertEquals(emptySet(), timeoutApi.activeTimeoutIds)
    }

    @Test
    fun `exit during an iteration delivers suspended exactly once and never rearms`() {
        val api = FakeSchedulingApi()
        val loop = TestWebEventLoop(api)
        var exited = false
        val handler = RecordingHandler(
            onNewEvents = { eventLoop, cause ->
                if (!exited && cause == StartCause.Poll) {
                    exited = true
                    eventLoop.exit()
                    eventLoop.exit()
                }
            },
        )
        loop.setControlFlow(ControlFlow.Poll)
        loop.runApp(handler)

        api.fireAnimationFrame(1)

        assertEquals(1, handler.trace.count { it == "suspended" })
        assertEquals(emptySet(), api.activeRafIds)
        assertEquals(emptySet(), api.activeTimeoutIds)
        assertEquals(listOf("requestAnimationFrame(1)"), api.operations)

        val traceAfterExit = handler.trace.toList()
        api.fireAnimationFrame(1)
        assertEquals(traceAfterExit, handler.trace)
        assertEquals(listOf("requestAnimationFrame(1)"), api.operations)
    }

    @Test
    fun `dom events remain routed to distinct owning windows`() {
        val firstBridge = RecordingBridge()
        val secondBridge = RecordingBridge()
        val loop = TestWebEventLoop(FakeSchedulingApi(), listOf(firstBridge, secondBridge))
        val firstWindow = loop.createWindow(WebWindowAttributes(canvasId = "first-canvas"))
        val secondWindow = loop.createWindow(WebWindowAttributes(canvasId = "second-canvas"))
        val handler = RecordingHandler()

        firstBridge.emit(WebWindowEvent.Focused(true))
        secondBridge.emit(WebWindowEvent.RedrawRequested)
        loop.pump(handler)

        assertEquals(listOf(WindowId(1L), WindowId(2L)), listOf(firstWindow.id, secondWindow.id))
        assertEquals(
            listOf(
                firstWindow.id to WindowEvent.Focused(true),
                secondWindow.id to WindowEvent.RedrawRequested,
            ),
            handler.windowEvents,
        )
    }

    private class TestWebEventLoop(
        api: BrowserSchedulingApi,
        private val bridges: List<RecordingBridge> = emptyList(),
    ) : WebEventLoop(api) {
        private var nextBridge = 0

        @Suppress("DEPRECATION")
        fun pump(handler: ApplicationHandler) = tick(handler)

        @Suppress("DEPRECATION")
        fun pump(handler: ApplicationHandler, legacyRafTimestamp: Double) =
            tick(handler, legacyRafTimestamp)

        override fun createDomBridge(): WebDomBridge = bridges[nextBridge++]
    }

    private class FakeSchedulingApi(
        var epochNowMillis: Long = 0L,
    ) : BrowserSchedulingApi {
        private var nextId = 1
        private val rafCallbacks = mutableMapOf<Int, () -> Unit>()
        private val timeoutCallbacks = mutableMapOf<Int, () -> Unit>()

        val operations = mutableListOf<String>()
        val activeRafIds = mutableSetOf<Int>()
        val activeTimeoutIds = mutableSetOf<Int>()

        override fun epochNowMillis(): Long = epochNowMillis

        override fun requestAnimationFrame(callback: () -> Unit): Int {
            val id = nextId++
            operations += "requestAnimationFrame($id)"
            rafCallbacks[id] = callback
            activeRafIds += id
            return id
        }

        override fun cancelAnimationFrame(id: Int) {
            operations += "cancelAnimationFrame($id)"
            activeRafIds -= id
        }

        override fun setTimeout(delayMillis: Int, callback: () -> Unit): Int {
            val id = nextId++
            operations += "setTimeout($id,$delayMillis)"
            timeoutCallbacks[id] = callback
            activeTimeoutIds += id
            return id
        }

        override fun clearTimeout(id: Int) {
            operations += "clearTimeout($id)"
            activeTimeoutIds -= id
        }

        fun fireAnimationFrame(id: Int) {
            activeRafIds -= id
            rafCallbacks.getValue(id).invoke()
        }

        fun fireTimeout(id: Int) {
            activeTimeoutIds -= id
            timeoutCallbacks.getValue(id).invoke()
        }
    }

    private class RecordingBridge : WebDomBridge {
        override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null

        override fun attach(targetElementId: String) = Unit

        override fun detach() = Unit

        fun emit(event: WebWindowEvent) {
            onWindowEvent?.invoke(event)
        }
    }

    private class RecordingHandler(
        private val onNewEvents: (ActiveEventLoop, StartCause) -> Unit = { _, _ -> },
        private val onWindowEvent: (ActiveEventLoop, WindowId, WindowEvent) -> Unit = { _, _, _ -> },
    ) : ApplicationHandler {
        val trace = mutableListOf<String>()
        val startCauses = mutableListOf<StartCause>()
        val windowEvents = mutableListOf<Pair<WindowId, WindowEvent>>()

        override fun resumed(eventLoop: ActiveEventLoop) {
            trace += "resumed"
        }

        override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) {
            startCauses += startCause
            trace += "new:${startCause.traceName()}"
            onNewEvents(eventLoop, startCause)
        }

        override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
            trace += "canCreateSurfaces"
        }

        override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
            windowEvents += windowId to event
            trace += "event"
            onWindowEvent(eventLoop, windowId, event)
        }

        override fun aboutToWait(eventLoop: ActiveEventLoop) {
            trace += "aboutToWait"
        }

        override fun suspended(eventLoop: ActiveEventLoop) {
            trace += "suspended"
        }
    }

}

private fun StartCause.traceName(): String = when (this) {
    StartCause.Init -> "Init"
    StartCause.Poll -> "Poll"
    is StartCause.WaitCancelled -> "WaitCancelled($requestedResume)"
    is StartCause.ResumeTimeReached -> "ResumeTimeReached($requestedResume,$start)"
}
