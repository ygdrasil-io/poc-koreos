package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import kotlin.test.*

class Win32WindowMutationTest {
    private fun driver(port: DeterministicWin32HostPort = DeterministicWin32HostPort()) =
        Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources)
    private suspend fun open(driver: Win32WindowRuntimeDriver, spec: WindowSpec = WindowSpec()): Window =
        assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(
            driver.manager.requestWindow(spec)).value.await()).window
    private suspend fun apply(window: Window, update: WindowUpdate): WindowUpdateOutcome =
        assertIs<KadreResult.Success<WindowUpdateOutcome>>(withTimeout(2000) { window.apply(update) }).value

    @Test fun titleAndClientSizeMutationCompleteThroughThePublicWindow() = runBlocking {
        driver().use { driver ->
            val window = open(driver)
            val result = assertIs<WindowUpdateOutcome.Applied>(apply(window, WindowUpdate(
                title = PropertyChange.Set("updated"), contentSize = PropertyChange.Set(LogicalSize(430.0, 290.0)))))
            assertEquals("updated", window.state.value.title)
            assertEquals(LogicalSize(430.0, 290.0), result.state.contentSize)
            assertEquals(result.state, window.state.value)
            assertEquals(PhysicalSize(430, 290), window.surface.state.value.physicalSize)
        }
    }

    @Test fun constraintsCanBeSetClearedAndReplacedWithAValidClientSize() = runBlocking {
        driver().use { driver ->
            val window = open(driver)
            assertIs<WindowUpdateOutcome.Applied>(apply(window, WindowUpdate(
                contentSize = PropertyChange.Set(LogicalSize(400.0, 300.0)),
                minimumSize = PropertyChange.Set(LogicalSize(200.0, 150.0)),
                maximumSize = PropertyChange.Set(LogicalSize(600.0, 450.0)), resizable = PropertyChange.Set(false))))
            assertEquals(LogicalSize(200.0, 150.0), window.state.value.minimumSize)
            assertEquals(LogicalSize(600.0, 450.0), window.state.value.maximumSize)
            assertFalse(window.state.value.resizable)
            apply(window, WindowUpdate(minimumSize = PropertyChange.Clear, maximumSize = PropertyChange.Clear))
            assertNull(window.state.value.minimumSize)
            assertNull(window.state.value.maximumSize)
        }
    }

    @Test fun borderlessChromeHasNoButtonsAndSystemChromeRestoresThem() = runBlocking {
        driver().use { driver ->
            val window = open(driver)
            apply(window, WindowUpdate(decorations = PropertyChange.Set(WindowDecorations.Borderless)))
            assertEquals(WindowDecorations.Borderless, window.state.value.decorations)
            assertEquals(WindowSystemButtons.None, window.state.value.systemButtons)
            apply(window, WindowUpdate(decorations = PropertyChange.Set(WindowDecorations.System)))
            assertEquals(WindowSystemButtons.All, window.state.value.systemButtons)
        }
    }

    @Test fun unsupportedFieldsAreIdentifiedAlongsideAppliedTitle() = runBlocking {
        driver().use { driver ->
            val window = open(driver)
            val result = assertIs<WindowUpdateOutcome.PartiallyApplied>(apply(window, WindowUpdate(
                title = PropertyChange.Set("supported"), transparency = PropertyChange.Set(true),
                contentProtection = PropertyChange.Set(true))))
            assertEquals(setOf(WindowProperty.Transparency, WindowProperty.ContentProtection), result.rejected.map { it.field }.toSet())
            assertEquals("supported", result.state.title)
            assertFalse(result.state.transparent)
        }
    }

    @Test fun hugeClientSizeFailsBeforeChangingTheNativeWindow() = runBlocking {
        driver().use { driver ->
            val window = open(driver)
            val before = window.state.value
            assertIs<KadreResult.Failure>(window.apply(WindowUpdate(title = PropertyChange.Set("must not be written"), contentSize = PropertyChange.Set(LogicalSize(1e30, 200.0)))))
            assertEquals(before, window.state.value)
        }
    }

    @Test fun effectiveReadbackAndExternalGeometryHaveDistinctOperationCorrelations() = runBlocking {
        val port = DeterministicWin32HostPort()
        driver(port).use { driver ->
            val window = open(driver)
            val events = mutableListOf<WindowEvent>()
            val collection = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { window.events.collect { events.add(it) } }
            port.titleReadback = { "native:$it" }
            val outcome = assertIs<WindowUpdateOutcome.Applied>(apply(window, WindowUpdate(title = PropertyChange.Set("requested"),
                outerPosition = PropertyChange.Set(PhysicalPoint(-25, 35)), contentSize = PropertyChange.Set(LogicalSize(400.0, 250.0)))))
            assertEquals("native:requested", outcome.state.title)
            assertEquals(PhysicalPoint(-25, 35), outcome.state.outerBounds?.origin)
            val hwnd = port.lastTopLevelHwnd()
            val external = PhysicalRect(PhysicalPoint(42, 51), PhysicalSize(516, 339))
            port.setWindowBounds(hwnd, external, false)
            yield()
            val geometry = events.filterIsInstance<WindowEvent.GeometryChanged>()
            assertEquals(2, geometry.size, "reentrant WM_SIZE during the local setter is not a second external event")
            assertEquals(outcome.operationId, geometry[0].operationId)
            assertNull(geometry[1].operationId)
            assertEquals(external, window.state.value.outerBounds)
            assertEquals(LogicalSize(500.0, 300.0), window.state.value.contentSize)
            collection.cancel()
        }
    }

    @Test fun aForeignResizeBeyondTrackLimitsPublishesActualGeometryWithoutInvalidConstraints() = runBlocking {
        val port = DeterministicWin32HostPort()
        driver(port).use { driver ->
            val window = open(driver, WindowSpec(contentSize = LogicalSize(400.0, 300.0), maximumSize = LogicalSize(500.0, 400.0)))
            port.setWindowBounds(port.lastTopLevelHwnd(), PhysicalRect(PhysicalPoint(0, 0), PhysicalSize(716, 539)), false)
            assertEquals(LogicalSize(700.0, 500.0), window.state.value.contentSize)
            assertNull(window.state.value.maximumSize)
        }
    }

    @Test fun aNativeFailureAfterAStyleWritePublishesTheActualCommittedChrome() = runBlocking {
        val port = DeterministicWin32HostPort()
        driver(port).use { driver ->
            val window = open(driver)
            port.afterStyleWrite = { error("injected after style commit") }
            assertIs<KadreResult.Failure>(window.apply(WindowUpdate(decorations = PropertyChange.Set(WindowDecorations.Borderless))))
            assertEquals(WindowDecorations.Borderless, window.state.value.decorations)
            assertEquals(WindowSystemButtons.None, window.state.value.systemButtons)
            assertEquals(WindowPhase.Open, window.state.value.phase)
        }
    }

    @Test fun closingFromAReentrantSetterDoesNotWriteGeometryOrResurrectThePeer() = runBlocking {
        val port = DeterministicWin32HostPort()
        driver(port).use { driver ->
            val window = open(driver)
            var boundsWrites = 0
            port.beforeBoundsWrite = { boundsWrites++ }
            port.beforeTitleWrite = { port.dispatchWindowMessage(port.lastTopLevelHwnd(), WM_DESTROY) }
            assertIs<KadreResult.Failure>(window.apply(WindowUpdate(title = PropertyChange.Set("close now"),
                contentSize = PropertyChange.Set(LogicalSize(400.0, 300.0)))))
            assertEquals(WindowPhase.Closed, window.state.value.phase)
            assertEquals(0, boundsWrites)
            assertTrue(driver.manager.state.value.windows.isEmpty())
        }
    }

    @Test fun aWorkerCanCancelAQueuedUpdateBeforeTheUiClaimsIt() = runBlocking {
        val owner = Thread.currentThread()
        val admitted = java.util.concurrent.Semaphore(0)
        val queue = Win32UiCommandQueue({ Thread.currentThread() === owner }, { admitted.release(); true })
        val port = object : DeterministicWin32HostPort() {
            override fun isMainThread() = Thread.currentThread() === owner
            override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) = queue.post(block, onFailure)
            override fun <T> onMainThread(block: () -> T): T = queue.invoke(block)
        }
        driver(port).use { driver ->
            val window = open(driver)
            val worker = launch(Dispatchers.Default) { window.apply(WindowUpdate(title = PropertyChange.Set("cancelled"))) }
            assertTrue(admitted.tryAcquire(2, java.util.concurrent.TimeUnit.SECONDS))
            worker.cancelAndJoin()
            queue.drain()
            assertEquals("", port.windowTitles.getValue(port.lastTopLevelHwnd()))
            assertEquals("", window.state.value.title)
            assertIs<WindowUpdateOutcome.Applied>(apply(window, WindowUpdate(title = PropertyChange.Set("next"))))
            assertEquals("next", window.state.value.title)
        }
        queue.close()
    }
}
