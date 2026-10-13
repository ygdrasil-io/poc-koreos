package org.graphiks.kadre.internal.win32

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kffi.win32.generated.IsWindow
import java.lang.foreign.MemorySegment
import kotlin.test.assertTrue

/**
 * P-1 / SN-3 (spec : scénarios O3) — preuves Windows réelles sur le runner
 * du gate `windows-contracts` ; jamais comptées comme réussies hors Windows.
 */
class Win32HostNativeContractTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @BeforeTest
    fun requireWindows() {
        if (System.getProperty("os.name")?.startsWith("Windows") != true) return
    }

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newPort(): KffiWin32HostPort = KffiWin32HostPort().also(cleanups::add)

    @Test
    fun hostCyclesThreeTimesOnWindows() {
        repeat(3) { cycle ->
            val port = newPort()
            val session = Win32HostSession.start(port)
            val hwnd = session.controlWindow.hwnd
            assertTrue(hwnd != 0L, "cycle $cycle: control window must exist")
            assertTrue(
                IsWindow(MemorySegment.ofAddress(hwnd)) != 0,
                "cycle $cycle: control window must be a live window",
            )
            session.teardown()
            assertEquals(
                0,
                IsWindow(MemorySegment.ofAddress(hwnd)),
                "cycle $cycle: control window must be destroyed by teardown",
            )
            port.close()
        }
    }

    @Test
    fun stopFromAnotherThreadWakesThePump() {
        val port = newPort()
        val session = Win32HostSession.start(port)
        cleanups.add(session)
        val stopped = CountDownLatch(1)
        val loop = thread(isDaemon = true) {
            var steps = 0
            while (steps < 200) {
                if (session.pump.runOnce(250) == PumpStep.Stopped) {
                    stopped.countDown()
                    return@thread
                }
                steps += 1
            }
        }
        // L'appelant n'est PAS le thread UI : le stop doit réveiller GetMessageW.
        port.requestStopFromAnyone(session.controlWindow.hwnd)
        assertTrue(stopped.await(10, TimeUnit.SECONDS), "stop must wake the blocked pump")
        thread {
            loop.join(5_000)
        }.join(5_100)
        assertTrue(session.pump.runOnce(0) == PumpStep.Stopped)
    }
}
