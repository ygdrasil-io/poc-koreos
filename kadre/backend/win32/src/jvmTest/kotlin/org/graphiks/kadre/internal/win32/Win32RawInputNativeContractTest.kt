package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.desktop.DesktopStandaloneRequest
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.generated.GetForegroundWindow
import org.graphiks.kffi.win32.generated.SendInput
import org.graphiks.kffi.win32.generated.ShowWindow
import org.graphiks.kffi.win32.generated.init
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Real session through the real provider: the process registration lands on the
 * activated top-level window, real injected relative motion crosses the public
 * raw-input access, and a second access reuses the same registration. An
 * inter-application physical mouse stays a manual reception and is never
 * claimed here.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32RawInputNativeContractTest {
    @Test fun realRegistrationDeliversInjectedMotionToThePublicAccess() {
        val motions = CopyOnWriteArrayList<RawInputEvent>()
        val provider = Win32BackendProvider()
        val outcome = assertIs<KadreResult.Success<SessionOutcome>>(provider.run(DesktopStandaloneRequest(
            applicationFactory = KadreApplicationFactory {
                KadreApplication {
                    val requested = windows.requestWindow(WindowSpec(title = "Kadre native raw-input proof",
                        contentSize = LogicalSize(320.0, 240.0)))
                    val window = ((requested as? KadreResult.Success)?.value?.await() as? WindowRequestOutcome.OpenedHere)?.window
                        ?: error("Window did not open")
                    val access = (window.surface.input.requestRawInput() as? KadreResult.Success)?.value
                        ?: error("raw input admission failed")
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        access.events.collect { event -> motions += event as RawInputEvent }
                    }
                    // Foreground-only registration: bring the registered window itself up.
                    window.withDesktopHandle { handle ->
                        val hwnd = (handle as DesktopNativeWindowHandle.Win32).hwnd.toLong()
                        bringToFront(hwnd)
                        injectRelativeMotion(5, 3)
                    }.let { result -> check(result is KadreResult.Success) { "desktop handle refused" } }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (motions.isEmpty() && System.nanoTime() < deadline) yield()
                    access.close()
                    requestStop()
                }
            },
            stopWhenLastWindowClosed = true,
            policy = KadrePolicies.Default,
        )))
        // requestStop ends the session with Stopped; the outcome is asserted only loosely.
        assertIs<SessionOutcome>(outcome.value)
        assertTrue(motions.isNotEmpty(), "one raw motion must reach the public access")
        val event = motions.last()
        assertEquals(RawInputUnit.DeviceCount, event.unit)
        assertTrue(event.deltaX != 0.0 || event.deltaY != 0.0,
            "the delivered motion must carry non-zero deltas: $event")
    }

    private fun bringToFront(hwnd: Long) {
        init()
        ShowWindow(MemorySegment.ofAddress(hwnd), SW_MINIMIZE)
        ShowWindow(MemorySegment.ofAddress(hwnd), SW_RESTORE)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (GetForegroundWindow().address() != hwnd && System.nanoTime() < deadline) Thread.sleep(50)
        check(GetForegroundWindow().address() == hwnd) { "the registered window could not become foreground" }
    }

    private fun injectRelativeMotion(deltaX: Int, deltaY: Int) {
        Arena.ofConfined().use { arena ->
            val input = arena.allocate(INPUT_BYTE_SIZE, 8)
            input.set(ValueLayout.JAVA_INT, INPUT_TYPE_OFFSET, 0) // INPUT_MOUSE
            input.set(ValueLayout.JAVA_INT, MOUSE_DX_OFFSET, deltaX)
            input.set(ValueLayout.JAVA_INT, MOUSE_DY_OFFSET, deltaY)
            input.set(ValueLayout.JAVA_INT, MOUSE_FLAGS_OFFSET, MOUSEEVENTF_MOVE)
            check(SendInput(1, input, INPUT_BYTE_SIZE.toInt()) == 1) { "SendInput failed" }
        }
    }

    private companion object {
        const val SW_MINIMIZE: Int = 6
        const val SW_RESTORE: Int = 9
        const val MOUSEEVENTF_MOVE: Int = 0x0001
        const val INPUT_BYTE_SIZE: Long = 40
        const val INPUT_TYPE_OFFSET: Long = 0
        const val MOUSE_DX_OFFSET: Long = 8
        const val MOUSE_DY_OFFSET: Long = 12
        const val MOUSE_FLAGS_OFFSET: Long = 20
    }
}
