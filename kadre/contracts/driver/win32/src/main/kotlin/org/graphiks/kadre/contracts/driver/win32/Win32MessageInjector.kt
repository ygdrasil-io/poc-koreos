package org.graphiks.kadre.contracts.driver.win32

import org.graphiks.kffi.win32.generated.PostMessageW
import org.graphiks.kffi.win32.generated.init
import org.graphiks.kffi.win32.managed.Win32ControlWindow
import java.lang.foreign.MemorySegment

/**
 * External O3 stimulus (spec R6): posts the phase 0 private test message
 * through the real Win32 message queue. Built on KFFI only — no Kadre
 * internals, no direct dispatch calls.
 */
public class Win32MessageInjector : AutoCloseable {
    public companion object {
        public fun isSupported(): Boolean =
            System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
    }

    init {
        check(isSupported()) {
            "Win32 message injection requires Windows; this platform (" +
                System.getProperty("os.name") + ") is unsupported — no native library was loaded."
        }
        init()
    }

    /** @return true when the message was queued (PostMessageW succeeded). */
    public fun postTestMessage(hwnd: Long, wParam: Long, lParam: Long): Boolean =
        PostMessageW(
            MemorySegment.ofAddress(hwnd),
            Win32ControlWindow.TEST_MESSAGE,
            wParam,
            lParam,
        ) != 0

    /** Window-queue input stimulus; this does not emulate hardware or change keyboard state. */
    public fun postWindowMessage(hwnd: Long, message: Int, wParam: Long = 0L, lParam: Long = 0L): Boolean =
        PostMessageW(MemorySegment.ofAddress(hwnd), message, wParam, lParam) != 0

    override fun close() {
        // Aucune ressource KFFI possédée : l'init générée est process-lifetime.
    }
}
