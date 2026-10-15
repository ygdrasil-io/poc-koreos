package org.graphiks.kadre.internal.win32

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.lang.foreign.MemorySegment

class Win32SurfaceFlowTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newDriver(): Pair<DeterministicWin32HostPort, Win32WindowRuntimeDriver> {
        val port = DeterministicWin32HostPort()
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = org.graphiks.kadre.policy.KadrePolicies.Default.resources,
        )
        cleanups.add(driver)
        return port to driver
    }

    private fun openWindow(driver: Win32WindowRuntimeDriver, title: String): org.graphiks.kadre.window.Window =
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = title))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val outcome = result.value.await()
            assertIs<WindowRequestOutcome.OpenedHere>(outcome).window
        }

    @Test
    fun wmSizeObservationPublishesReadBackMetrics() {
        val (port, driver) = newDriver()
        runBlocking {
            val window = openWindow(driver, "sized")
            val hwnd = port.lastTopLevelHwnd()
            port.scriptedClientSize[hwnd] = 500 to 400
            port.dispatchWindowMessage(hwnd, 0x0005) // WM_SIZE
            withTimeout(2_000) {
                window.state.first { it.contentSize.width == 500.0 && it.contentSize.height == 400.0 }
            }
        }
    }

    @Test
    fun dpiObservationKeepsSurfaceMetricsAndInputCoordinatesCoherent() = runBlocking {
        val (port, driver) = newDriver()
        val window = openWindow(driver, "dpi-input")
        val hwnd = port.lastTopLevelHwnd()
        port.scriptedClientSize[hwnd] = 1000 to 750
        port.scriptedScaleFactor[hwnd] = 1.25
        port.dispatchWindowMessage(hwnd, WM_DPICHANGED)
        assertEquals(1.25, window.surface.state.value.scaleFactor)
        assertEquals(org.graphiks.kadre.surface.PhysicalSize(1000, 750), window.surface.state.value.physicalSize)
        port.dispatchWindowMessage(hwnd, 0x200, 0, 125L or (250L shl 16))
        assertEquals(org.graphiks.kadre.surface.LogicalPoint(100.0, 200.0), window.surface.input.state.value.pointers.single().position)
    }

    @Test
    fun redrawRequestsCoalesceDrainAndRearm() {
        val (port, driver) = newDriver()
        runBlocking {
            val window = openWindow(driver, "redraw")
            val hwnd = port.lastTopLevelHwnd()
            window.surface.requestRedraw()
            window.surface.requestRedraw()
            // Le runtime coalesque : une seule invalidation pour N demandes
            // pendant qu'un redraw est pending (D3).
            assertEquals(1, port.invalidateCount.get(), "the runtime coalesces the second request")
            // La pompe draine la file : WM_PAINT n'est délivré que si invalidé.
            port.dispatchWindowMessage(hwnd, 0x000F)
            assertTrue(port.validateCount.get() >= 1, "the drain validates the region")
            // Le cycle se réarme : une nouvelle demande ré-invalide.
            window.surface.requestRedraw()
            assertEquals(2, port.invalidateCount.get(), "the cycle re-arms after the drain")
        }
    }

    @Test
    fun redrawOnClosedWindowFailsClosed() {
        val (port, driver) = newDriver()
        runBlocking {
            val window = openWindow(driver, "closed-soon")
            val surfaceId = window.surface.id
            window.close()
            port.invalidateCount.set(0)
            val result = window.surface.requestRedraw()
            val failure = assertIs<KadreResult.Failure>(result)
            assertEquals(0, port.invalidateCount.get(), "no invalidation may reach the port")
        }
    }
}
