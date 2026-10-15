package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.generated.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.*

/** Real User32 setters/readback through public Window operations; no display-mode changes. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32WindowMutationNativeContractTest {
    private suspend fun fixture(block: suspend CoroutineScope.(KffiWin32HostPort, Window, Long) -> Unit) = coroutineScope {
        val port = KffiWin32HostPort()
        port.createControlWindow { _, _, _ -> 0 }
        val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter = org.graphiks.kadre.internal.runtime.RuntimeFailureReporter { failures += it })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec(title = "native mutation"))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            block(port, window, hwnd)
            assertTrue(failures.isEmpty(), "native callback failures: $failures")
        } finally { driver.close(); port.close() }
    }

    private suspend fun CoroutineScope.applyPumped(port: KffiWin32HostPort, window: Window, update: WindowUpdate): WindowUpdateOutcome {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { window.apply(update) }
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!pending.isCompleted && System.nanoTime() < deadline) { port.drainOneMessage(1); yield() }
        assertTrue(pending.isCompleted, "native mutation did not complete within 3 seconds")
        return assertIs<KadreResult.Success<WindowUpdateOutcome>>(pending.await()).value
    }

    // Independent oracle: four signed LONGs, not the driver's copied snapshot.
    private fun outer(hwnd: Long): PhysicalRect = Arena.ofConfined().use { arena ->
        val rect = arena.allocate(16, 4)
        assertTrue(GetWindowRect(MemorySegment.ofAddress(hwnd), rect) != 0)
        val x = rect.get(ValueLayout.JAVA_INT, 0)
        val y = rect.get(ValueLayout.JAVA_INT, 4)
        PhysicalRect(PhysicalPoint(x, y), PhysicalSize(rect.get(ValueLayout.JAVA_INT, 8) - x, rect.get(ValueLayout.JAVA_INT, 12) - y))
    }

    @Test fun publicMutationsMatchNativeClientRectStylesAndTitle() = runBlocking {
        fixture { port, window, hwnd ->
            val outcome = assertIs<WindowUpdateOutcome.Applied>(applyPumped(port, window, WindowUpdate(
                title = PropertyChange.Set("readback \uD83D\uDE80"), contentSize = PropertyChange.Set(LogicalSize(420.0, 280.0)),
                outerPosition = PropertyChange.Set(PhysicalPoint(40, 60)), resizable = PropertyChange.Set(false),
                systemButtons = PropertyChange.Set(WindowSystemButtons.CloseOnly))))
            assertEquals("readback \uD83D\uDE80", outcome.state.title)
            assertEquals(outer(hwnd), outcome.state.outerBounds)
            val style = GetWindowLongPtrW(MemorySegment.ofAddress(hwnd), -16).toInt()
            assertEquals(0, style and 0x00070000)
            assertTrue(style and 0x00080000 != 0)
            Arena.ofConfined().use { arena ->
                val rect = arena.allocate(16, 4)
                assertTrue(GetClientRect(MemorySegment.ofAddress(hwnd), rect) != 0)
                assertEquals(PhysicalSize(rect.get(ValueLayout.JAVA_INT, 8), rect.get(ValueLayout.JAVA_INT, 12)), window.surface.state.value.physicalSize)
                val scale = GetDpiForWindow(MemorySegment.ofAddress(hwnd)) / 96.0
                assertEquals(window.surface.state.value.physicalSize.toLogical(scale), outcome.state.contentSize)
            }
        }
    }

    @Test fun nativeBorderlessRoundTripRestoresTheExactWindowedStyleAndRectangle() = runBlocking {
        fixture { port, window, hwnd ->
            applyPumped(port, window, WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(430.0, 310.0)),
                minimumSize = PropertyChange.Set(LogicalSize(200.0, 150.0)), maximumSize = PropertyChange.Set(LogicalSize(600.0, 450.0))))
            val original = outer(hwnd)
            val style = GetWindowLongPtrW(MemorySegment.ofAddress(hwnd), -16)
            applyPumped(port, window, WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
            assertEquals(FullscreenMode.Borderless, window.state.value.fullscreen)
            assertEquals(port.monitorBounds(hwnd), outer(hwnd))
            assertEquals(0, GetWindowLongPtrW(MemorySegment.ofAddress(hwnd), -16).toInt() and WIN32_CHROME_MASK)
            assertNull(window.state.value.minimumSize)
            assertNull(window.state.value.maximumSize)
            applyPumped(port, window, WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed)))
            assertEquals(original, outer(hwnd))
            assertEquals(style, GetWindowLongPtrW(MemorySegment.ofAddress(hwnd), -16))
            assertEquals(FullscreenMode.Windowed, window.state.value.fullscreen)
            assertEquals(LogicalSize(200.0, 150.0), window.state.value.minimumSize)
            assertEquals(LogicalSize(600.0, 450.0), window.state.value.maximumSize)
        }
    }

    @Test fun nativeMinmaxCallbackAppliesAndClearsTheRequestedTrackLimits() = runBlocking {
        fixture { port, window, hwnd ->
            applyPumped(port, window, WindowUpdate(contentSize = PropertyChange.Set(LogicalSize(400.0, 300.0)),
                minimumSize = PropertyChange.Set(LogicalSize(200.0, 150.0)), maximumSize = PropertyChange.Set(LogicalSize(600.0, 450.0))))
            Arena.ofConfined().use { arena ->
                val info = arena.allocate(40, 4)
                SendMessageW(MemorySegment.ofAddress(hwnd), WM_GETMINMAXINFO, 0, info.address())
                val scale = GetDpiForWindow(MemorySegment.ofAddress(hwnd)) / 96.0
                val minimum = LogicalSize(200.0, 150.0).toPhysical(scale)
                val maximum = LogicalSize(600.0, 450.0).toPhysical(scale, PixelRounding.Floor)
                val minOuter = port.outerSizeForClient(hwnd, minimum.width, minimum.height)
                val maxOuter = port.outerSizeForClient(hwnd, maximum.width, maximum.height)
                assertEquals(minOuter.first, info.get(ValueLayout.JAVA_INT, 24))
                assertEquals(minOuter.second, info.get(ValueLayout.JAVA_INT, 28))
                assertEquals(maxOuter.first, info.get(ValueLayout.JAVA_INT, 32))
                assertEquals(maxOuter.second, info.get(ValueLayout.JAVA_INT, 36))
                applyPumped(port, window, WindowUpdate(minimumSize = PropertyChange.Clear, maximumSize = PropertyChange.Clear))
                info.fill(0)
                SendMessageW(MemorySegment.ofAddress(hwnd), WM_GETMINMAXINFO, 0, info.address())
                assertNull(window.state.value.minimumSize)
                assertNull(window.state.value.maximumSize)
                assertTrue(info.get(ValueLayout.JAVA_INT, 32) != maxOuter.first || info.get(ValueLayout.JAVA_INT, 36) != maxOuter.second)
            }
        }
    }

    @Test fun anOverflowingFrameInclusiveMaximumIsRejectedWithoutFailingTheSession() = runBlocking {
        fixture { port, window, _ ->
            val before = window.state.value
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                window.apply(WindowUpdate(title = PropertyChange.Set("must not commit"),
                    maximumSize = PropertyChange.Set(LogicalSize(Int.MAX_VALUE.toDouble(), 1000.0))))
            }
            val deadline = System.nanoTime() + 3_000_000_000L
            while (!pending.isCompleted && System.nanoTime() < deadline) { port.drainOneMessage(1); yield() }
            assertTrue(pending.isCompleted)
            assertIs<KadreResult.Failure>(pending.await())
            assertEquals(before, window.state.value)
            assertEquals(WindowPhase.Open, window.state.value.phase)
            applyPumped(port, window, WindowUpdate(title = PropertyChange.Set("still alive")))
            assertEquals("still alive", window.state.value.title)
        }
    }

    @Test fun publicWorkerUpdatesAndClosesWithoutWaitingOnAStoppedPump() {
        var effective: WindowState? = null
        val outcome = runKadreApplication(options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                withTimeout(6000) {
                    val request = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec())).value
                    val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
                    val applied = assertIs<KadreResult.Success<WindowUpdateOutcome>>(window.apply(WindowUpdate(title = PropertyChange.Set("worker mutation"),
                        contentSize = PropertyChange.Set(LogicalSize(360.0, 240.0)))))
                    effective = assertIs<WindowUpdateOutcome.Applied>(applied.value).state
                    window.close()
                }
            })
        assertEquals("worker mutation", assertNotNull(effective).title)
        assertTrue(outcome == SessionOutcome.Completed || outcome is SessionOutcome.Stopped, "session outcome: $outcome")
    }
}
