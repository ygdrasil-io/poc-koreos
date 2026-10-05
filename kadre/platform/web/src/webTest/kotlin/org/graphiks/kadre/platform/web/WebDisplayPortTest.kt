package org.graphiks.kadre.platform.web

import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.display.DisplayType
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.surface.PhysicalPoint
import org.graphiks.kadre.surface.PhysicalRect
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The browsing context cannot enumerate real displays, so the gate mandates exactly one inventory:
 * the primary viewport, published as a `HostViewport` display. These tests pin the exact shape the
 * gate requires and the failure that stands in whenever the viewport is unmeasurable — a partial or
 * empty inventory is never an acceptable answer.
 */
class WebDisplayPortTest {
    private class FakeDisplaySource(var metrics: WebViewportMetrics?) : WebDisplaySource {
        val listeners = mutableListOf<() -> Unit>()
        var subscriptionCloses = 0
        var closed = false
        override fun current() = metrics
        override fun observe(listener: () -> Unit): AutoCloseable {
            listeners += listener
            return AutoCloseable {
                subscriptionCloses += 1
                listeners.remove(listener)
            }
        }
        override fun close() {
            closed = true
        }
    }

    @Test
    fun snapshotIsExactlyTheHostViewportFallback() = runTest {
        val port = WebDisplayPort(FakeDisplaySource(WebViewportMetrics(1600, 900, 2.0, 24)))

        val snapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(port.requestSnapshot()).value
        val bounds = PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(3200, 1800))
        assertEquals(0L, snapshot.primaryKey)
        assertEquals(1, snapshot.displays.size)
        val display = snapshot.displays.single()
        assertEquals(0L, display.key)
        assertEquals(DisplayType.HostViewport, display.type)
        assertEquals(null, display.name)
        assertEquals(bounds, display.bounds)
        assertEquals(bounds, display.workArea)
        assertEquals(2.0, display.scaleFactor)
        assertEquals(0L, display.currentModeKey)
        assertEquals(1, display.modes.size)
        val mode = display.modes.single()
        assertEquals(0L, mode.key)
        assertEquals(PhysicalSize(3200, 1800), mode.physicalSize)
        assertEquals(null, mode.refreshRateHz)
        assertEquals(24, mode.bitDepth)
        assertEquals(Capability.Supported(Unit, FeatureAvailability.Available), port.enumerationCapability)

        // The physical size is the CSS size scaled by the dpr and rounded, not truncated: the same
        // metrics a device with a fractional scale factor reports (1501.5 rounds up to 1502).
        val fractional = WebDisplayPort(FakeDisplaySource(WebViewportMetrics(1001, 901, 1.5, 30)))
        val rounded = assertIs<KadreResult.Success<DisplayPortSnapshot>>(fractional.requestSnapshot()).value
        assertEquals(PhysicalSize(1502, 1352), rounded.displays.single().bounds.size)
    }

    @Test
    fun observerIsReInvokedOnSourceChange() = runTest {
        val source = FakeDisplaySource(WebViewportMetrics(1600, 900, 2.0, 24))
        val port = WebDisplayPort(source)

        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver { received += it }
        // The install subscribes; it does not publish. The runtime requests its initial inventory
        // itself, and the port re-emits when the source fires.
        assertEquals(0, received.size)

        source.metrics = WebViewportMetrics(800, 600, 1.0, 24)
        source.listeners.single().invoke()

        assertEquals(1, received.size)
        val snapshot = assertIs<KadreResult.Success<DisplayPortSnapshot>>(received.single()).value
        assertEquals(PhysicalSize(800, 600), snapshot.displays.single().bounds.size)
        assertEquals(1.0, snapshot.displays.single().scaleFactor)
    }

    @Test
    fun missingMetricsFailAsTemporarilyUnavailable() = runTest {
        val source = FakeDisplaySource(null)
        val port = WebDisplayPort(source)

        val direct = assertIs<KadreResult.Failure>(port.requestSnapshot())
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), direct.reason)

        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver { received += it }
        source.listeners.single().invoke()
        assertEquals(1, received.size)
        val observed = assertIs<KadreResult.Failure>(received.single())
        assertEquals(KadreFailure.TemporarilyUnavailable(retryable = true), observed.reason)

        // Invalid metrics are as good as missing ones — the port never derives a partial inventory
        // from a dpr it cannot trust, even if its source failed to filter it.
        source.metrics = WebViewportMetrics(100, 100, 0.0, 24)
        assertIs<KadreResult.Failure>(port.requestSnapshot())
    }

    @Test
    fun closeUnsubscribesExactlyOnce() = runTest {
        val source = FakeDisplaySource(WebViewportMetrics(1600, 900, 2.0, 24))
        val port = WebDisplayPort(source)

        val received = mutableListOf<KadreResult<DisplayPortSnapshot>>()
        port.installSnapshotObserver { received += it }
        val lateFire = source.listeners.single()

        port.close()
        port.close()

        lateFire()
        assertEquals(1, source.subscriptionCloses, "the source subscription is withdrawn exactly once")
        assertTrue(source.listeners.isEmpty(), "the port's own source subscription is gone")
        assertTrue(source.closed, "the port closes the source it owns")
        assertEquals(0, received.size, "a late fire after close publishes nothing")
    }
}
