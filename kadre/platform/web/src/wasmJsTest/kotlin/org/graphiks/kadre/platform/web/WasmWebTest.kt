@file:OptIn(ExperimentalWasmJsInterop::class)

package org.graphiks.kadre.platform.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class WasmWebTest {
    @Test
    fun zeroSizedElementKeepsPositiveMetrics() {
        val host = document.createElement("div") as HTMLElement
        host.style.width = "0px"
        host.style.height = "0px"
        document.body!!.appendChild(host)

        val metrics = host.readSurfaceMetricsForTest(scaleFactor = 1.0)

        assertTrue(metrics.logicalWidth >= 1.0, "a collapsed element must not publish a zero logical size")
        assertTrue(metrics.logicalHeight >= 1.0, "a collapsed element must not publish a zero logical size")
        assertTrue(metrics.physicalSize.width >= 1, "physical size follows the clamped logical size")
        host.remove()
    }

    @Test
    fun deviceScaleFactorChangeIsReadBack() {
        val host = document.createElement("div") as HTMLElement
        host.style.width = "100px"
        host.style.height = "50px"
        document.body!!.appendChild(host)

        val atOne = host.readSurfaceMetricsForTest(scaleFactor = 1.0)
        val atTwo = host.readSurfaceMetricsForTest(scaleFactor = 2.0)

        assertEquals(LogicalSize(100.0, 50.0), atTwo.logicalSize)
        assertEquals(atOne.logicalSize, atTwo.logicalSize, "a scale change must not move the CSS box")
        assertEquals(PhysicalSize(200, 100), atTwo.physicalSize, "physical size follows the device scale")
        host.remove()
    }

    @Test
    fun aRealElementResizeReachesTheAttachedSurface() = runTest {
        val host = existingHostElement()
        val scopeReady = CompletableDeferred<KadreScope>()
        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(this) {
                scopeReady.complete(this)
                awaitCancellation()
            },
        ).value
        testScheduler.runCurrent()

        try {
            val surface = scopeReady.await().primarySurface.value ?: error("a web session exposes a primary surface")
            assertEquals(LogicalSize(320.0, 180.0), surface.state.value.logicalSize)

            host.style.width = "400px"
            awaitRealFrames("the surface reports the element the browser resized") {
                surface.state.value.logicalSize == LogicalSize(400.0, 180.0)
            }
        } finally {
            session.requestStop()
            testScheduler.runCurrent()
            host.remove()
        }
    }

    @Test
    fun theBrowsingContextDeviceScaleIsReadBackOnObservation() = runTest {
        val host = existingHostElement()
        val originalScale = window.devicePixelRatio
        setDevicePixelRatio(2.0)
        val port = WasmWebDomPort(host)
        try {
            assertEquals(
                2.0,
                port.initialSnapshot.scaleFactor,
                "attach reads the scale of the browsing context that owns the element",
            )
            assertEquals(LogicalSize(320.0, 180.0), port.initialSnapshot.logicalSize)

            val delivered = mutableListOf<WebSurfaceMetrics>()
            port.installMetricsObserver { delivered += it }

            // A zoom or a monitor move changes the ratio without moving the CSS box, so the next
            // observation is the only thing that can pick the new scale up.
            setDevicePixelRatio(3.0)
            host.style.width = "400px"
            awaitRealFrames("the port re-reads the scale the browsing context now reports") {
                delivered.any { it.physicalSize == PhysicalSize(1200, 540) }
            }

            val rescaled = delivered.last { it.physicalSize == PhysicalSize(1200, 540) }
            assertEquals(3.0, rescaled.scaleFactor)
            assertEquals(
                LogicalSize(400.0, 180.0),
                rescaled.logicalSize,
                "a scale change must not move the CSS box",
            )
        } finally {
            port.release()
            setDevicePixelRatio(originalScale)
            host.remove()
        }
        assertEquals(originalScale, window.devicePixelRatio, "the scale override must not outlive its test")
    }

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also {
            it.style.width = "320px"
            it.style.height = "180px"
            document.body!!.appendChild(it)
        }

    /**
     * Redefines the device pixel ratio the browsing context reports, as a zoom or a monitor move
     * would.
     *
     * The definition replaces the browser's own accessor for the rest of the page, so the test
     * writes the value it read back and asserts that the browser reports it again.
     */
    private fun setDevicePixelRatio(value: Double) {
        jsSetDevicePixelRatio(value)
    }

    /**
     * Waits for browser frames to deliver an observation, without the virtual clock of [runTest].
     *
     * The rendering steps that deliver a `ResizeObserver` callback only run on the real event loop,
     * so the wait has to leave the test dispatcher behind.
     */
    private suspend fun awaitRealFrames(what: String, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            val deadline = TimeSource.Monotonic.markNow() + 1.seconds
            while (!condition() && deadline.hasNotPassedNow()) delay(5)
        }
        assertTrue(condition(), "the browser never delivered: $what")
    }
}

@JsFun("(value) => { Object.defineProperty(window, 'devicePixelRatio', { configurable: true, value: value }); }")
private external fun jsSetDevicePixelRatio(value: Double)
