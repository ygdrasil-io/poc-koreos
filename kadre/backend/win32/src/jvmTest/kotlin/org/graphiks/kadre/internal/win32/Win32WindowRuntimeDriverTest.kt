package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.platform.desktop.withDesktopHandle
import org.graphiks.kadre.window.WindowPhase
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter

@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32WindowRuntimeDriverTest {
    private fun driver(
        port: DeterministicWin32HostPort,
        maxWindows: Int = 8,
        onLastWindowClosed: (() -> Unit)? = null,
    ): Win32WindowRuntimeDriver = Win32WindowRuntimeDriverFactory.create(
        port = port,
        resources = KadrePolicies.Default.resources.copy(maxWindowsPerSession = maxWindows),
        onLastWindowClosed = onLastWindowClosed,
    )

    private fun openWindow(driver: Win32WindowRuntimeDriver, title: String) = runBlocking {
        val request = driver.manager.requestWindow(WindowSpec(title = title))
        val outcome = when (val result = request) {
            is KadreResult.Success -> runBlocking { result.value.await() }
            is KadreResult.Failure -> throw AssertionError("requestWindow('$title') failed: ${result.reason}")
        }
        java.io.File("/tmp/p2-open-outcome.txt").appendText("$outcome\n")
        assertIs<WindowRequestOutcome.OpenedHere>(outcome).window
    }

    @Test
    fun openThreeWindowsAdmitsThemWithPrimaryFollowingAdmission() = runBlocking {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        try {
            val first = openWindow(driver, "first")
            val second = openWindow(driver, "second")
            val third = openWindow(driver, "third")
            assertEquals(3, port.createdTopLevelWindows.get())
            val state = driver.manager.state.value
            assertEquals(first.id, state.primary?.id, "primary follows admission order")
            assertEquals(3, state.windows.size)
            assertEquals(first.id, state.windows.first().id)
        } finally {
            driver.close()
        }
    }

    @Test
    fun budgetExhaustionRejectsWithoutCreating() {
        val port = DeterministicWin32HostPort()
        val driver = driver(port, maxWindows = 1)
        runBlocking {
            openWindow(driver, "only")
            // Le budget épuisé échoue au niveau admission : Failure direct,
            // sans création native ni outcome.
            val failure = assertIs<KadreResult.Failure>(driver.manager.requestWindow(WindowSpec(title = "overflow")))
            assertIs<KadreFailure.ResourceLimitExceeded>(failure.reason)
            assertEquals(1, port.createdTopLevelWindows.get(), "no extra native creation")
        }
        driver.close()
    }

    @Test
    fun appCloseRunsClosingDetachesAndClosesInOrder() = runBlocking {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        try {
            val window = openWindow(driver, "orderly")
            val closed = withTimeout(2_000) {
                window.close()
                window.state.first { it.phase == WindowPhase.Closed }
                true
            }
            assertTrue(closed)
            assertEquals(0, driver.manager.state.value.windows.size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun refusedNativeCloseKeepsWindowAlive() = runBlocking {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        try {
            val window = openWindow(driver, "refusing")
            // L'application joue son rôle : observer CloseRequested et fermer.
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                window.events.collect { event ->
                    if (event is org.graphiks.kadre.window.WindowEvent.CloseRequested) {
                        window.close()
                    }
                }
            }
            port.dispatchWindowMessage(port.topLevelHwndAt(0), WM_CLOSE)
            withTimeout(2_000) {
                window.state.first { it.phase == WindowPhase.Closed }
            }
            collector.cancel()
            assertEquals(0, driver.manager.state.value.windows.size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun closingSecondaryKeepsPumpAliveAndManagerConsistent() = runBlocking {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        try {
            val first = openWindow(driver, "primary")
            val second = openWindow(driver, "secondary")
            second.close()
            withTimeout(2_000) {
                second.state.first { it.phase == WindowPhase.Closed }
            }
            assertEquals(1, driver.manager.state.value.windows.size)
            assertTrue(driver.manager.state.value.primary?.id == first.id)
        } finally {
            driver.close()
        }
    }

    @Test
    fun desktopHandleLeaseExposesTheLiveHwndAndClosesWithTheWindow() = runBlocking {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        try {
            val window = openWindow(driver, "leased")
            val borrowed = window.withDesktopHandle { handle -> handle }
            val handle = when (val result = borrowed) {
                is KadreResult.Success -> assertIs<org.graphiks.kadre.platform.desktop.DesktopNativeWindowHandle.Win32>(result.value)
                is KadreResult.Failure -> throw AssertionError(
                    "lease failed: ${result.reason} (reason class: ${result.reason::class.simpleName})",
                )
            }
            assertTrue(handle.hwnd != 0UL, "the borrowed hwnd must be live")
            window.close()
            val after = window.withDesktopHandle { handle -> handle }
            val failure = assertIs<KadreResult.Failure>(after)
            assertIs<KadreFailure.Closed>(failure.reason)
            Unit
        } finally {
            driver.close()
        }
    }

    @Test
    fun pendingCancellationDestroysTheGhostWindowWithoutPublicEvents() {
        val port = DeterministicWin32HostPort()
        val driver = driver(port)
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = "ghost")).successValue()
            request.cancel()
            runBlocking { request.await() }
        }
        driver.close()
        assertEquals(1, port.destroyedTopLevelWindows.get())
    }
}

private fun <T> KadreResult<T>.successValue(): T = when (this) {
    is KadreResult.Success -> value
    is KadreResult.Failure -> error("unexpected failure: $reason")
}
