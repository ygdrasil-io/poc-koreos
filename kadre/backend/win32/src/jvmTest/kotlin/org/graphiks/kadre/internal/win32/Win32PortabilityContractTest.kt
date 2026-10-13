package org.graphiks.kadre.internal.win32

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * W32-003 portable half (spec R1): the off-Windows properties are provable
 * through the injectable OS gate everywhere; the real native half runs in
 * Win32NativeContractTest on Windows only.
 */
class Win32PortabilityContractTest {
    @Test
    fun unsupportedOsFailsActionablyBeforeNative() {
        val host = Win32ControlHost(osGate = { false })
        val failure = runCatching { host.open { _, _, _, _ -> 0L } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "expected actionable failure, got $failure")
        assertTrue(
            failure.message?.contains("Windows") == true,
            "failure must name the platform requirement: ${failure.message}",
        )
        assertTrue(host.dispatches.isEmpty(), "no dispatch may be recorded before the OS gate passes")
        host.close()
        assertTrue(host.isClosed, "close on a never-opened host is still idempotent")
    }
}
