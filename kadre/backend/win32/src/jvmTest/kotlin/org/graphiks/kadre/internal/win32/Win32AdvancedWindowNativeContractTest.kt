package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.generated.*
import org.graphiks.kffi.win32.managed.*
import java.lang.foreign.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32AdvancedWindowNativeContractTest {
    private suspend fun fixture(block: suspend CoroutineScope.(KffiWin32HostPort, Window, Long) -> Unit) = coroutineScope {
        val port = KffiWin32HostPort(); port.createControlWindow { _, _, _ -> 0 }
        val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter = org.graphiks.kadre.internal.runtime.RuntimeFailureReporter { failures += it })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec(title = "phase 5 complete", outerPosition = PhysicalPoint(40, 40), contentSize = LogicalSize(320.0, 220.0)))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            block(port, window, hwnd)
            assertTrue(failures.isEmpty(), "native callbacks: $failures")
        } finally { driver.close(); port.close() }
    }
    private suspend fun CoroutineScope.applyPumped(port: KffiWin32HostPort, window: Window, update: WindowUpdate): WindowUpdateOutcome {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { window.apply(update) }
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!pending.isCompleted && System.nanoTime() < deadline) { port.drainOneMessage(1); yield() }
        assertTrue(pending.isCompleted)
        return assertIs<KadreResult.Success<WindowUpdateOutcome>>(pending.await()).value
    }
    private fun image() = BinaryImage(ByteArray(64) { if (it % 4 == 3) 255.toByte() else (it * 3).toByte() }, ImageFormat.Rgba8, PhysicalSize(4, 4))

    @Test fun exactLevelsIconSlotsAndMonitorAffinityMatchNativeReadback() = runBlocking { fixture { port, window, hwnd ->
        val h = MemorySegment.ofAddress(hwnd); val icon = image()
        assertIs<WindowUpdateOutcome.Applied>(applyPumped(port, window, WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating), icon = PropertyChange.Set(icon), contentProtection = PropertyChange.Set(true))))
        assertTrue(GetWindowLongPtrW(h, -20) and 8L != 0L)
        val handle = SendMessageW(h, 0x7f, 1, 0); assertTrue(handle != 0L)
        assertEquals(handle, SendMessageW(h, 0x7f, 0, 0)); assertEquals(icon, window.state.value.icon)
        Arena.ofConfined().use { arena -> val affinity = arena.allocate(4, 4)
            assertTrue(GetWindowDisplayAffinity(h, affinity) != 0); assertEquals(1, affinity.get(ValueLayout.JAVA_INT, 0))
        }
        val foreground = GetForegroundWindow().address()
        window.requestAttention(WindowAttention.Informational); window.requestAttention(WindowAttention.Critical); window.requestAttention(WindowAttention.None)
        assertEquals(foreground, GetForegroundWindow().address(), "attention does not activate a window")
        applyPumped(port, window, WindowUpdate(level = PropertyChange.Set(WindowLevel.Normal), icon = PropertyChange.Clear, contentProtection = PropertyChange.Set(false)))
        assertEquals(0L, GetWindowLongPtrW(h, -20) and 8L); assertEquals(0L, SendMessageW(h, 0x7f, 1, 0)); assertFalse(window.state.value.contentProtection)
    } }

    @Test fun clientCursorUsesNativeHandlesAndClipReleasePreservesForeignOwnership() = runBlocking { fixture { port, window, hwnd ->
        val h = MemorySegment.ofAddress(hwnd)
        val sharedDefault = LoadCursorW(MemorySegment.NULL, MemorySegment.ofAddress(32512)).address()
        SetCursor(MemorySegment.ofAddress(sharedDefault))
        window.surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)))
        assertEquals(sharedDefault, GetCursor().address(), "changing a policy cannot steal a shared cursor from another window")
        for (cursor in listOf(CursorStyle.System(CursorIcon.Text), CursorStyle.Custom(CursorImage(image(), PhysicalPoint(2, 1))), CursorStyle.Hidden)) {
            assertIs<SurfaceUpdateOutcome.Applied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(cursor)))).value)
            assertEquals(1, SendMessageW(h, 0x20, hwnd, 1).toInt())
            val handle = GetCursor().address()
            if (cursor == CursorStyle.Hidden) assertEquals(0L, handle) else assertTrue(handle != 0L)
            if (cursor is CursorStyle.System) assertEquals(LoadCursorW(MemorySegment.NULL, MemorySegment.ofAddress(32513)).address(), handle)
        }
        // This process cannot steal focus from another desktop client. Exercise success when the OS grants it,
        // and assert the explicit refusal path otherwise; foreign-clip preservation is unconditional.
        SetForegroundWindow(h)
        val confined = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))).value
        if (GetForegroundWindow().address() == hwnd) {
            println("confinement: foreground acquisition exercised")
            assertIs<SurfaceUpdateOutcome.Applied>(confined)
            assertEquals(PointerCaptureMode.Confined, window.surface.state.value.pointerCapture)
            SendMessageW(h, 0x1f, 0, 0)
            assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
        } else { println("confinement: foreign foreground refusal exercised"); assertIs<SurfaceUpdateOutcome.PartiallyApplied>(confined) }
        val original = Win32PointerConfinement.currentClip()
        Arena.ofConfined().use { arena -> val rect = arena.allocate(16, 4)
            rect.set(ValueLayout.JAVA_INT, 0, original.x + 3); rect.set(ValueLayout.JAVA_INT, 4, original.y + 3)
            rect.set(ValueLayout.JAVA_INT, 8, original.x + 83); rect.set(ValueLayout.JAVA_INT, 12, original.y + 83)
            assertTrue(ClipCursor(rect) != 0)
            try {
                val foreign = Win32PointerConfinement.currentClip()
                assertIs<SurfaceUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))).value)
                window.surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.None)))
                assertEquals(foreign, Win32PointerConfinement.currentClip())
            } finally { ClipCursor(MemorySegment.NULL) }
        }
    } }

    @Test fun systemMinimizeMaximizeRestoreAndVisibilityPublishNativeObservations() = runBlocking { fixture { port, window, hwnd ->
        val h = MemorySegment.ofAddress(hwnd)
        assertTrue(GetWindowLongPtrW(h, -16) and 0xb0000L == 0xb0000L, "native system menu and min/max buttons")
        val positive = window.surface.state.value.physicalSize
        SendMessageW(h, 0x112, 0xf020, 0) // SC_MINIMIZE
        assertTrue(IsIconic(h) != 0); assertEquals(SurfaceVisibility.Hidden, window.surface.state.value.visibility)
        assertEquals(positive, window.surface.state.value.physicalSize)
        SendMessageW(h, 0x112, 0xf120, 0) // SC_RESTORE
        assertEquals(0, IsIconic(h)); assertEquals(SurfaceVisibility.Visible, window.surface.state.value.visibility)
        SendMessageW(h, 0x112, 0xf030, 0) // SC_MAXIMIZE
        assertTrue(GetWindowLongPtrW(h, -16) and 0x01000000L != 0L)
        assertEquals(port.readClientPhysicalSize(hwnd).first, window.surface.state.value.physicalSize.width)
        SendMessageW(h, 0x112, 0xf120, 0)
        ShowWindow(h, 0); assertEquals(SurfaceVisibility.Hidden, window.surface.state.value.visibility)
        ShowWindow(h, 5); assertEquals(SurfaceVisibility.Visible, window.surface.state.value.visibility)
    } }

    @Test fun workerCommandExecutesInsideTheRealNativeSystemMenuLoop() {
        KffiWin32HostPort().use { port ->
            port.createControlWindow { _, _, _ -> 0 }
            val entered = CountDownLatch(1); val inside = AtomicBoolean(false); val progressed = AtomicBoolean(false)
            val window = port.createTopLevelWindow("native modal menu", Win32WindowDecorations.System, 300, 200) { hwnd, message, w, l ->
                if (message == 0x211) { inside.set(true); entered.countDown() }
                if (message == 0x212) inside.set(false)
                port.defWindowProc(hwnd, message, w, l)
            }
            port.showWindow(window.hwnd)
            val h = MemorySegment.ofAddress(window.hwnd)
            val worker = Thread {
                if (entered.await(3, TimeUnit.SECONDS)) port.postOnMainThread({ progressed.set(inside.get()); PostMessageW(h, 0x1f, 0, 0) }, {})
                // Cancellation fallback keeps a broken command wake from hanging this test.
                Thread.sleep(300); PostMessageW(h, 0x1f, 0, 0)
            }
            worker.start()
            try {
                assertEquals(0, Win32WindowOperations.showSystemMenu(window.hwnd, 60, 60))
                worker.join(4000)
                assertEquals(0L, entered.count); assertTrue(progressed.get(), "worker command must run while WM_ENTERMENULOOP is active")
                assertFalse(inside.get())
            } finally { PostMessageW(h, 0x1f, 0, 0); port.destroyWindow(window.hwnd) }
        }
    }

    @Test fun moveAndResizeUseCopiedCoordinatesAndRealSystemModalLoops() {
        KffiWin32HostPort().use { port ->
            port.createControlWindow { _, _, _ -> 0 }
            var entering: CountDownLatch? = null
            var enteredCount = 0; var exitedCount = 0
            val drags = mutableListOf<Pair<Long, Long>>()
            val window = port.createTopLevelWindow("native modal drag", Win32WindowDecorations.System, 300, 200) { hwnd, message, w, l ->
                if (message == 0x231) { enteredCount++; entering?.countDown() }
                if (message == 0x232) exitedCount++
                if (message == 0xa1) { drags += w to l; 0L } else port.defWindowProc(hwnd, message, w, l)
            }
            port.setWindowBounds(window.hwnd, PhysicalRect(PhysicalPoint(40, 40), PhysicalSize(320, 240)), false)
            port.showWindow(window.hwnd)
            val h = MemorySegment.ofAddress(window.hwnd)
            try {
                for (hit in listOf(2, 17)) {
                    // Native callback delivery proves the copied event coordinates. A posted press
                    // cannot assert physical button state, so the actual User32 loop is entered
                    // by its keyboard system command, without injecting hardware input.
                    port.beginMoveResize(window.hwnd, hit, 10, 10)
                    assertEquals(hit.toLong(), drags.last().first)
                    Arena.ofConfined().use { arena ->
                        val point = arena.allocate(8, 4); point.set(ValueLayout.JAVA_INT, 0, 10); point.set(ValueLayout.JAVA_INT, 4, 10)
                        assertTrue(ClientToScreen(h, point) != 0)
                        val x = (drags.last().second and 0xffff).toShort().toInt()
                        val y = ((drags.last().second ushr 16) and 0xffff).toShort().toInt()
                        assertEquals(point.get(ValueLayout.JAVA_INT, 0), x); assertEquals(point.get(ValueLayout.JAVA_INT, 4), y)
                    }
                    val started = CountDownLatch(1); entering = started
                    val worker = Thread {
                        started.await(2, TimeUnit.SECONDS)
                        PostMessageW(h, 0x100, 0x1b, 0); PostMessageW(h, 0x1f, 0, 0)
                    }
                    worker.start()
                    SendMessageW(h, 0x112, if (hit == 2) 0xf010 else 0xf000, 0)
                    worker.join(3000)
                    assertEquals(0L, started.count, "WM_ENTERSIZEMOVE must come from User32, hit=$hit")
                }
                assertEquals(2, enteredCount); assertEquals(enteredCount, exitedCount)
                assertEquals(0L, GetCapture().address(), "modal cancellation leaves no native capture")
            } finally { PostMessageW(h, 0x1f, 0, 0); port.destroyWindow(window.hwnd) }
        }
    }
}
