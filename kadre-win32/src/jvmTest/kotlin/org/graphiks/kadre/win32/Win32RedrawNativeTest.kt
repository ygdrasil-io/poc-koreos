package org.graphiks.kadre.win32

import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.ffi.win32.allocateMsg
import org.graphiks.kadre.ffi.win32.generated.DispatchMessageW
import org.graphiks.kadre.ffi.win32.generated.PeekMessageW
import org.graphiks.kadre.ffi.win32.generated.TranslateMessage
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Win32RedrawNativeTest {

    @Test
    fun `requestRedraw produces one paint cycle that becomes quiescent`() {
        if (!isWindowsHost()) return

        val redrawCount = AtomicInteger()
        KadreWndProc.install { _, event ->
            if (event is WindowEvent.RedrawRequested) redrawCount.incrementAndGet()
        }
        var window: Win32Window? = null
        try {
            val createdWindow = assertNotNull(
                Win32Window.create(
                    WindowAttributes(
                        title = "Kadre redraw test",
                        // Hidden HWNDs do not synthesize WM_PAINT on this host, so keep the
                        // test window visible but tiny and outside the virtual desktop.
                        visible = true,
                        position = PhysicalPosition(-32_000, -32_000),
                        size = PhysicalSize(32, 32),
                    ),
                ),
            )
            window = createdWindow

            Arena.ofConfined().use { arena ->
                val message = arena.allocateMsg()
                drainInitialMessages(message, deadlineAfterMillis(1_000))
                redrawCount.set(0)

                createdWindow.requestRedraw()

                val deadline = deadlineAfterMillis(2_000)
                var previousCount = 0
                var lastChange = System.nanoTime()
                var quiescent = false
                while (System.nanoTime() < deadline) {
                    val dispatched = pumpAvailableMessages(message, deadline)
                    val currentCount = redrawCount.get()
                    if (currentCount != previousCount) {
                        previousCount = currentCount
                        lastChange = System.nanoTime()
                    }
                    if (
                        currentCount > 0 &&
                        dispatched == 0 &&
                        System.nanoTime() - lastChange >= QUIESCENCE_NANOS
                    ) {
                        quiescent = true
                        break
                    }
                    Thread.yield()
                }

                assertTrue(redrawCount.get() >= 1, "requestRedraw() must produce WM_PAINT")
                assertTrue(quiescent, "WM_PAINT delivery must become quiescent after EndPaint")
            }
        } finally {
            try {
                window?.close()
            } finally {
                KadreWndProc.uninstall()
            }
        }
    }
}

private const val MAX_MESSAGES_PER_BATCH = 256
private const val QUIESCENCE_NANOS = 50_000_000L

private fun isWindowsHost(): Boolean =
    System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

private fun deadlineAfterMillis(milliseconds: Long): Long =
    System.nanoTime() + milliseconds * 1_000_000L

private fun drainInitialMessages(message: MemorySegment, deadline: Long) {
    while (System.nanoTime() < deadline) {
        if (pumpAvailableMessages(message, deadline) == 0) return
        Thread.yield()
    }
}

private fun pumpAvailableMessages(message: MemorySegment, deadline: Long): Int {
    var dispatched = 0
    while (dispatched < MAX_MESSAGES_PER_BATCH && System.nanoTime() < deadline) {
        if (PeekMessageW(message, MemorySegment.NULL, 0, 0, PM_REMOVE) == 0) break
        TranslateMessage(message)
        DispatchMessageW(message)
        dispatched++
    }
    return dispatched
}
