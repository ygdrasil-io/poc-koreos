package org.graphiks.kadre.web

import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.Insets
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.StartCause
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCloseAndSafeAreaTest {

    @Test
    fun `close is terminal idempotent and purges queued work except one Destroyed`() {
        val api = CloseSchedulingApi()
        val bridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        lateinit var capturedCallback: (WebWindowEvent) -> Unit
        val handler = CloseRecordingHandler { _, windowId, event ->
            if (event == WindowEvent.Destroyed) {
                assertEquals(window.id, windowId)
                window.close()
                window.requestRedraw()
                capturedCallback(WebWindowEvent.Focused(false))
            }
        }
        loop.runApp(handler)
        capturedCallback = bridge.onWindowEvent!!

        bridge.emit(WebWindowEvent.Focused(true))
        window.requestRedraw()
        window.close()
        window.close()
        capturedCallback(WebWindowEvent.Focused(false))
        loop.pump(handler)
        loop.pump(handler)

        assertEquals(1, bridge.detachCount)
        val expectedEvents: List<Pair<WindowId, WindowEvent>> =
            listOf(window.id to WindowEvent.Destroyed)
        assertEquals(expectedEvents, handler.windowEvents)
    }

    @Test
    fun `close during a snapshot drops later snapshot work for that window`() {
        val api = CloseSchedulingApi()
        val bridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val handler = CloseRecordingHandler { _, _, event ->
            if (event == WindowEvent.Focused(true)) {
                window.close()
            }
        }
        loop.runApp(handler)

        bridge.emit(WebWindowEvent.Focused(true))
        bridge.emit(WebWindowEvent.RedrawRequested)
        loop.pump(handler)

        val expectedEvents: List<Pair<WindowId, WindowEvent>> = listOf(
            window.id to WindowEvent.Focused(true),
            window.id to WindowEvent.Destroyed,
        )
        assertEquals(expectedEvents, handler.windowEvents)
        assertEquals(1, bridge.detachCount)
    }

    @Test
    fun `close during ScaleFactorChanged drops paired Resized and later work`() {
        val api = CloseSchedulingApi()
        val bridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val handler = CloseRecordingHandler { _, _, event ->
            if (event is WindowEvent.ScaleFactorChanged) {
                window.close()
            }
        }
        loop.runApp(handler)

        WebMetricsTransactions.dispatch(
            bridge,
            WebMetricsTransaction(
                scaleFactor = 3.0,
                physicalSize = PhysicalSize(900, 450),
            ),
        )
        bridge.emit(WebWindowEvent.Focused(true))
        loop.pump(handler)

        val expectedEvents: List<Pair<WindowId, WindowEvent>> = listOf(
            window.id to WindowEvent.ScaleFactorChanged(3.0),
            window.id to WindowEvent.Destroyed,
        )
        assertEquals(expectedEvents, handler.windowEvents)
        assertEquals(1, bridge.detachCount)
    }

    @Test
    fun `closing one of two windows preserves scheduler and last close cancels RAF`() {
        val api = CloseSchedulingApi()
        val firstBridge = CloseRecordingBridge()
        val secondBridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(firstBridge, secondBridge))
        val first = loop.createWindow(WebWindowAttributes(canvasId = "first"))
        val second = loop.createWindow(WebWindowAttributes(canvasId = "second"))
        loop.setControlFlow(ControlFlow.Poll)
        loop.runApp(CloseRecordingHandler())

        assertEquals(setOf(1), api.activeRafIds)
        first.close()
        assertEquals(setOf(1), api.activeRafIds)
        assertEquals(emptyList(), api.cancelledRafIds)

        second.close()
        assertEquals(emptySet(), api.activeRafIds)
        assertEquals(listOf(1), api.cancelledRafIds)
        assertEquals(1, firstBridge.detachCount)
        assertEquals(1, secondBridge.detachCount)
    }

    @Test
    fun `last close cancels owned timeout while another live window does not`() {
        val api = CloseSchedulingApi(epochNowMillis = 9_000L)
        val firstBridge = CloseRecordingBridge()
        val secondBridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(firstBridge, secondBridge))
        val first = loop.createWindow(WebWindowAttributes(canvasId = "first"))
        val second = loop.createWindow(WebWindowAttributes(canvasId = "second"))
        loop.setControlFlow(ControlFlow.WaitUntil(10_000L))
        loop.runApp(CloseRecordingHandler())

        assertEquals(setOf(1), api.activeTimeoutIds)
        first.close()
        assertEquals(setOf(1), api.activeTimeoutIds)
        assertEquals(emptyList(), api.clearedTimeoutIds)

        second.close()
        assertEquals(emptySet(), api.activeTimeoutIds)
        assertEquals(listOf(1), api.clearedTimeoutIds)
    }

    @Test
    fun `closing current shared bridge owner restores the previous live window routes`() {
        val api = CloseSchedulingApi()
        val bridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(bridge, bridge))
        val first = loop.createWindow(WebWindowAttributes(canvasId = "first"))
        val second = loop.createWindow(WebWindowAttributes(canvasId = "second"))
        val handler = CloseRecordingHandler()
        loop.runApp(handler)

        second.close()
        bridge.emit(WebWindowEvent.Focused(true))
        assertTrue(
            WebMetricsTransactions.dispatch(
                bridge,
                WebMetricsTransaction(
                    scaleFactor = 3.0,
                    physicalSize = PhysicalSize(900, 450),
                ),
            ),
        )
        loop.pump(handler)

        val expectedEvents: List<Pair<WindowId, WindowEvent>> = listOf(
            second.id to WindowEvent.Destroyed,
            first.id to WindowEvent.Focused(true),
            first.id to WindowEvent.ScaleFactorChanged(3.0),
            first.id to WindowEvent.Resized(PhysicalSize(900, 450)),
        )
        assertEquals(expectedEvents, handler.windowEvents)
        assertEquals(0, bridge.detachCount)

        first.close()
        assertEquals(1, bridge.detachCount)
    }

    @Test
    fun `bridge Destroyed follows the same terminal close path as pagehide`() {
        val api = CloseSchedulingApi()
        val bridge = CloseRecordingBridge()
        val loop = CloseTestEventLoop(api, listOf(bridge))
        val window = loop.createWindow(WebWindowAttributes(canvasId = "canvas"))
        val handler = CloseRecordingHandler()
        loop.runApp(handler)
        val capturedCallback = bridge.onWindowEvent!!

        capturedCallback(WebWindowEvent.Destroyed)
        capturedCallback(WebWindowEvent.Destroyed)
        capturedCallback(WebWindowEvent.Focused(true))
        loop.pump(handler)

        assertEquals(1, bridge.detachCount)
        val expectedEvents: List<Pair<WindowId, WindowEvent>> =
            listOf(window.id to WindowEvent.Destroyed)
        assertEquals(expectedEvents, handler.windowEvents)
        assertNull(bridge.onWindowEvent)
    }

    @Test
    fun `safe area converts CSS doubles to physical pixels and observes live DPR`() {
        val readings = platformSafeAreaReadings(
            cssInsets = CssSafeAreaTestInsets(
                top = 10.25,
                bottom = 5.0,
                left = 0.0,
                right = 1.5,
            ),
            firstDpr = 2.0,
            secondDpr = 3.0,
        )

        assertEquals(Insets(top = 21, bottom = 10, left = 0, right = 3), readings.first)
        assertEquals(Insets(top = 31, bottom = 15, left = 0, right = 5), readings.second)
        assertTrue(readings.sameBridgeInstance)
    }

    @Test
    fun `safe area measurement removes its temporary element when computed style throws`() {
        assertTrue(platformSafeAreaElementIsRemovedAfterFailure())
    }
}

internal data class CssSafeAreaTestInsets(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
)

internal data class PlatformSafeAreaReadings(
    val first: Insets<Int>,
    val second: Insets<Int>,
    val sameBridgeInstance: Boolean,
)

internal expect fun platformSafeAreaReadings(
    cssInsets: CssSafeAreaTestInsets,
    firstDpr: Double,
    secondDpr: Double,
): PlatformSafeAreaReadings

internal expect fun platformSafeAreaElementIsRemovedAfterFailure(): Boolean

private class CloseTestEventLoop(
    api: BrowserSchedulingApi,
    private val bridges: List<CloseRecordingBridge>,
) : WebEventLoop(api) {
    private var nextBridge = 0

    @Suppress("DEPRECATION")
    fun pump(handler: ApplicationHandler) = tick(handler)

    override fun createDomBridge(): WebDomBridge = bridges[nextBridge++]
}

private class CloseSchedulingApi(
    var epochNowMillis: Long = 0L,
) : BrowserSchedulingApi {
    private var nextId = 1
    private val rafCallbacks = mutableMapOf<Int, () -> Unit>()
    private val timeoutCallbacks = mutableMapOf<Int, () -> Unit>()

    val activeRafIds = mutableSetOf<Int>()
    val activeTimeoutIds = mutableSetOf<Int>()
    val cancelledRafIds = mutableListOf<Int>()
    val clearedTimeoutIds = mutableListOf<Int>()

    override fun epochNowMillis(): Long = epochNowMillis

    override fun requestAnimationFrame(callback: () -> Unit): Int = nextId++.also { id ->
        rafCallbacks[id] = callback
        activeRafIds += id
    }

    override fun cancelAnimationFrame(id: Int) {
        cancelledRafIds += id
        activeRafIds -= id
    }

    override fun setTimeout(delayMillis: Int, callback: () -> Unit): Int = nextId++.also { id ->
        timeoutCallbacks[id] = callback
        activeTimeoutIds += id
    }

    override fun clearTimeout(id: Int) {
        clearedTimeoutIds += id
        activeTimeoutIds -= id
    }
}

private class CloseRecordingBridge : WebDomBridge {
    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
    var detachCount = 0
        private set

    override fun attach(targetElementId: String) = Unit

    override fun detach() {
        detachCount += 1
    }

    fun emit(event: WebWindowEvent) {
        onWindowEvent?.invoke(event)
    }
}

private class CloseRecordingHandler(
    private val onWindowEvent: (ActiveEventLoop, WindowId, WindowEvent) -> Unit = { _, _, _ -> },
) : ApplicationHandler {
    val windowEvents = mutableListOf<Pair<WindowId, WindowEvent>>()

    override fun resumed(eventLoop: ActiveEventLoop) = Unit
    override fun newEvents(eventLoop: ActiveEventLoop, startCause: StartCause) = Unit
    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) = Unit
    override fun aboutToWait(eventLoop: ActiveEventLoop) = Unit
    override fun suspended(eventLoop: ActiveEventLoop) = Unit

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: WindowEvent) {
        windowEvents += windowId to event
        onWindowEvent(eventLoop, windowId, event)
    }
}
