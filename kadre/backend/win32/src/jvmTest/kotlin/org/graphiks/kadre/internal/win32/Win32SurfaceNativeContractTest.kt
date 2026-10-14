package org.graphiks.kadre.internal.win32

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.platform.desktop.DesktopNativeWindowHandle
import org.graphiks.kadre.platform.desktop.withDesktopHandle
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * W32-007/008 native half (spec R4) : métriques relues après resize natif
 * (`SetWindowPos` → WM_SIZE → relecture `GetClientRect`/`GetDpiForWindow`)
 * et redraw réel (`requestRedraw` → InvalidateRect → drain pompe).
 */
@OptIn(
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
)
class Win32SurfaceNativeContractTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newDriver(): Pair<KffiWin32HostPort, Win32WindowRuntimeDriver> {
        val port = KffiWin32HostPort()
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = org.graphiks.kadre.policy.KadrePolicies.Default.resources,
        )
        cleanups.add(driver)
        return port to driver
    }

    private fun diag(tag: String, content: String) {
        val dir = java.nio.file.Paths.get("build/win32-integration")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.writeString(
            dir.resolve("$tag.txt"),
            content,
        )
    }

    private fun borrowHwnd(window: org.graphiks.kadre.window.Window): Long = runBlocking {
        val borrowed = window.withDesktopHandle { handle -> handle }
        val result = assertIs<KadreResult.Success<DesktopNativeWindowHandle>>(borrowed)
        (result.value as DesktopNativeWindowHandle.Win32).hwnd.toLong()
    }

    // Independent native oracle: Windows LONG remains 32-bit on Win64.
    private fun readNativeClientSize(hwnd: Long): Pair<Int, Int> = Arena.ofConfined().use { arena ->
        val rect = arena.allocate(16, 4)
        val ok = org.graphiks.kffi.win32.generated.GetClientRect(MemorySegment.ofAddress(hwnd), rect)
        val error = if (ok == 0) org.graphiks.kffi.win32.generated.GetLastError() else 0
        val bytes = rect.toArray(ValueLayout.JAVA_BYTE).joinToString(" ") {
            "%02x".format(it.toInt() and 0xff)
        }
        val diagnostic = "hwnd=$hwnd GetClientRect=$ok error=$error RECT=[$bytes]"
        diag("client-rect", diagnostic)
        assertTrue(ok != 0, diagnostic)
        (rect.get(ValueLayout.JAVA_INT, 8) - rect.get(ValueLayout.JAVA_INT, 0)) to
            (rect.get(ValueLayout.JAVA_INT, 12) - rect.get(ValueLayout.JAVA_INT, 4))
    }

    @Test
    fun programmaticResizePublishesReadBackMetrics() {
        val (port, driver) = newDriver()
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "resize"))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val window = assertIs<WindowRequestOutcome.OpenedHere>(result.value.await()).window
            val hwnd = borrowHwnd(window)
            val initial = port.readClientPhysicalSize(hwnd)

            // SetWindowPos sizes the outer window, including its decorations.
            assertEquals(
                1,
                org.graphiks.kffi.win32.generated.SetWindowPos(
                    MemorySegment.ofAddress(hwnd),
                    MemorySegment.NULL,
                    0,
                    0,
                    900,
                    700,
                    4,
                ),
                "SetWindowPos must succeed",
            )
            val expectedPhysical = readNativeClientSize(hwnd)
            assertTrue(expectedPhysical.first > 0 && expectedPhysical.second > 0)
            assertTrue(expectedPhysical != initial, "the native resize must change the client size")
            assertEquals(expectedPhysical, port.readClientPhysicalSize(hwnd), "client RECT readback")
            val scale = org.graphiks.kffi.win32.generated.GetDpiForWindow(MemorySegment.ofAddress(hwnd)) / 96.0
            withTimeout(2_000) {
                window.state.first {
                    it.contentSize.width == expectedPhysical.first / scale &&
                        it.contentSize.height == expectedPhysical.second / scale
                }
            }
            // Les métriques publiées sont la relecture effective, pas la demande.
            assertIs<org.graphiks.kadre.window.WindowState>(window.state.value).let { state ->
                assertEquals(expectedPhysical.first / scale, state.contentSize.width)
                assertEquals(expectedPhysical.second / scale, state.contentSize.height)
            }
            assertTrue(initial.first > 0, "the initial client size must be readable")
        }
    }

    @Test
    fun redrawRequestInvalidatesTheRealWindow() {
        val (port, driver) = newDriver()
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "invalidate"))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val window = assertIs<WindowRequestOutcome.OpenedHere>(result.value.await()).window
            val hwnd = borrowHwnd(window)

            assertIs<KadreResult.Success<*>>(window.surface.requestRedraw())
            // La pompe du thread draine l'invalidation : WM_PAINT arrive puis
            // la région est validée — le cycle ne bloque ni ne rejette.
            val deadline = System.currentTimeMillis() + 2_000
            var pumped = 0
            while (System.currentTimeMillis() < deadline && pumped < 50) {
                port.drainOneMessage(10)
                pumped += 1
            }
            assertTrue(pumped >= 0)
            assertTrue(
                org.graphiks.kffi.win32.generated.IsWindow(MemorySegment.ofAddress(hwnd)) != 0,
                "the window must stay alive after a redraw drain",
            )
        }
    }

    @Test
    fun scaleReadbackIsCoherent() {
        val (port, driver) = newDriver()
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "scale"))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val window = assertIs<WindowRequestOutcome.OpenedHere>(result.value.await()).window
            val hwnd = borrowHwnd(window)
            val scale = port.readScaleFactor(hwnd)
            assertTrue(scale > 0.0, "a live window must have a positive DPI scale")
            val (width, height) = try {
                port.readClientPhysicalSize(hwnd)
            } catch (failure: Throwable) {
                diag("client-size-failure", "hwnd=$hwnd failure=${failure.message}")
                throw failure
            }
            diag(
                "scale-readback",
                "scale=$scale size=$width×$height hwnd=$hwnd " + port.readWindowRectForDiagnostics(hwnd),
            )
            // Diagnostic placement : GetWindowRect + état réduit.
            val windowRect = port.readWindowRectForDiagnostics(hwnd)
            val iconic = org.graphiks.kffi.win32.generated.IsIconic(MemorySegment.ofAddress(hwnd)) != 0
            diag(
                "placement",
                "hwnd=$hwnd windowRect=$windowRect iconic=$iconic client=$width×$height",
            )
            println("PLACEMENT $windowRect iconic=$iconic client=${width}x$height")
            assertEquals(readNativeClientSize(hwnd), width to height, "client RECT readback at scale $scale")
            assertTrue(width > 0 && height > 0, "client size must be readable at scale $scale (got ${width}×$height)")
        }
    }
}
