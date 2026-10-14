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
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kffi.win32.generated.IsWindow
import org.graphiks.kadre.diagnostics.KadreResult
import java.lang.foreign.MemorySegment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * W32-004/006 native half (spec R4): three real top-level windows through a
 * full lifecycle on the runner's desktop session.
 */
@OptIn(
    org.graphiks.kadre.diagnostics.KadrePlatformApi::class,
    org.graphiks.kadre.diagnostics.DelicateKadreApi::class,
)
class Win32WindowsNativeContractTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newDriver(onLastWindowClosed: (() -> Unit)? = null): Win32WindowRuntimeDriver {
        val port = KffiWin32HostPort()
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = org.graphiks.kadre.policy.KadrePolicies.Default.resources,
            onLastWindowClosed = onLastWindowClosed,
        )
        cleanups.add(driver)
        return driver
    }

    private fun openWindow(driver: Win32WindowRuntimeDriver, title: String): org.graphiks.kadre.window.Window =
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = title))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val outcome = result.value.await()
            assertIs<WindowRequestOutcome.OpenedHere>(outcome).window
        }

    private fun borrowHwnd(window: org.graphiks.kadre.window.Window): Long =
        runBlocking {
            val borrowed = window.withDesktopHandle { handle -> handle }
            val result = assertIs<KadreResult.Success<DesktopNativeWindowHandle>>(borrowed)
            (result.value as DesktopNativeWindowHandle.Win32).hwnd.toLong()
        }

    @Test
    fun windowLifecycleCyclesThroughThreeRealTopLevelWindows() {
        val driver = newDriver()
        repeat(3) { cycle ->
            runBlocking {
                val window = openWindow(driver, "cycle-$cycle")
                val hwnd = borrowHwnd(window)
                assertTrue(IsWindow(MemorySegment.ofAddress(hwnd)) != 0, "cycle $cycle: window must be live")
                window.close()
                withTimeout(2_000) { window.state.first { state -> state.phase == WindowPhase.Closed } }
                assertEquals(
                    0,
                    IsWindow(MemorySegment.ofAddress(hwnd)),
                    "cycle $cycle: window must be destroyed",
                )
            }
        }
    }

    @Test
    fun closingSecondaryKeepsTheSessionAliveThenLastWindowStopsIt() {
        val stopped = CountDownLatch(1)
        val driver = newDriver(onLastWindowClosed = { stopped.countDown() })
        runBlocking {
            val first = openWindow(driver, "last-1")
            val second = openWindow(driver, "last-2")
            second.close()
            withTimeout(2_000) { second.state.first { state -> state.phase == WindowPhase.Closed } }
            assertTrue(
                driver.manager.state.value.windows.isNotEmpty(),
                "the session must stay alive while a window remains",
            )
            first.close()
        }
        assertTrue(stopped.await(5, TimeUnit.SECONDS), "the last window close must arm the stop")
        driver.close()
    }
}
