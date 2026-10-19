package org.graphiks.kadre.internal.win32

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import org.graphiks.kadre.input.DropOfferState
import org.graphiks.kadre.input.DropOfferTerminationReason
import org.graphiks.kadre.input.DropTransfer
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowCloseOutcome
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateKadreApi::class)
class Win32DropFlowTest {
    private val cleanups = ArrayList<AutoCloseable>()

    @AfterTest
    fun teardown() {
        cleanups.reversed().forEach { runCatching(it::close) }
        cleanups.clear()
    }

    private fun newDriver(dropAvailable: Boolean): Pair<DeterministicWin32HostPort, Win32WindowRuntimeDriver> {
        val port = DeterministicWin32HostPort()
        port.dropAvailable = dropAvailable
        cleanups.add(port)
        val driver = Win32WindowRuntimeDriverFactory.create(
            port = port,
            resources = KadrePolicies.Default.resources,
        )
        cleanups.add(driver)
        return port to driver
    }

    private fun openWindow(driver: Win32WindowRuntimeDriver, title: String): org.graphiks.kadre.window.Window =
        runBlocking {
            val request = driver.manager.requestWindow(WindowSpec(title = title))
            val result = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(request)
            val outcome = result.value.await()
            assertIs<WindowRequestOutcome.OpenedHere>(outcome).window
        }

    private fun acceptDropEntered(window: org.graphiks.kadre.window.Window) {
        val registration = window.surface.installInteractionHandler(InteractionHandler { context, event ->
            if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
        })
        assertTrue(registration is KadreResult.Success, "the win32 surface installs interaction handlers")
    }

    private fun textSource(closed: java.util.concurrent.atomic.AtomicBoolean, payload: String = "kadre drop") =
        Win32DropTransferSource(listOf(Win32BytesDropItemSource(
            DropItemDescriptor(null, payload.length.toLong(), listOf("text/plain"), DropItemKind.Text),
            payload.toByteArray(Charsets.UTF_8),
        ))) { closed.set(true) }

    @Test
    fun acceptedDropRoutesEnterMovePerformAndClaimToThePublicTransfer() = runBlocking {
        val (port, driver) = newDriver(dropAvailable = true)
        val window = openWindow(driver, "drop-accepted")
        val hwnd = port.lastTopLevelHwnd()
        assertTrue(port.dropObservers.containsKey(hwnd), "the drop target installs once the surface is ready")

        acceptDropEntered(window)
        val events = Channel<InputEvent>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            window.surface.input.events.collect { event ->
                if (event is InputEvent.DropEntered || event is InputEvent.DropMoved || event is InputEvent.Dropped) events.send(event)
            }
        }
        try {
            val closed = java.util.concurrent.atomic.AtomicBoolean(false)
            assertTrue(port.dispatchDropEnter(hwnd, textSource(closed), 10.0, 20.0), "an accepted drop answers DROPEFFECT_COPY")
            assertFalse(closed.get(), "an admitted source stays owned by the runtime")
            port.dispatchDropMoved(hwnd, 30.0, 40.0)
            port.dispatchDropPerformed(hwnd, 50.0, 60.0)

            val entered = withTimeout(5.seconds) { assertIs<InputEvent.DropEntered>(events.receive()) }
            val moved = withTimeout(5.seconds) { assertIs<InputEvent.DropMoved>(events.receive()) }
            val dropped = withTimeout(5.seconds) { assertIs<InputEvent.Dropped>(events.receive()) }
            assertEquals(10.0, entered.position.x)
            assertEquals(20.0, entered.position.y)
            assertEquals(entered.offer.id, moved.offerId)
            assertTrue(entered.offer === dropped.offer, "the same runtime offer carries enter and drop")
            assertEquals(DropOfferState.TransferAvailable, dropped.offer.state.value)

            val transfer = assertIs<KadreResult.Success<DropTransfer>>(dropped.offer.claimTransfer()).value
            try {
                val bytes = StringBuilder()
                transfer.items.single().collectBytes(256) { bytes.append(String(it, Charsets.UTF_8)) }
                assertEquals("kadre drop", bytes.toString())
                assertEquals(DropOfferState.Claimed, dropped.offer.state.value)
            } finally {
                transfer.close()
            }
            assertTrue(closed.get(), "closing the transfer closes the backend source")
        } finally {
            collector.cancel()
            events.close()
        }
    }

    @Test
    fun rejectedDropClosesTheSourceAnswersNoneAndIgnoresLaterObservations() = runBlocking {
        val (port, driver) = newDriver(dropAvailable = true)
        val window = openWindow(driver, "drop-rejected")
        val hwnd = port.lastTopLevelHwnd()
        // No interaction handler: the synchronous admission finds no AcceptDrop answer.
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        assertFalse(port.dispatchDropEnter(hwnd, textSource(closed), 1.0, 2.0), "an unanswered admission rejects")
        assertTrue(closed.get(), "a rejected source is closed before the native return")
        port.dispatchDropMoved(hwnd, 3.0, 4.0)
        port.dispatchDropPerformed(hwnd, 5.0, 6.0) // stale: no active offer, must be ignored
    }

    @Test
    fun exitTerminatesTheOfferAndLaterObservationsAreStale() = runBlocking {
        val (port, driver) = newDriver(dropAvailable = true)
        val window = openWindow(driver, "drop-exited")
        val hwnd = port.lastTopLevelHwnd()
        acceptDropEntered(window)
        val events = Channel<InputEvent>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            window.surface.input.events.collect { event ->
                if (event is InputEvent.DropEntered || event is InputEvent.DropExited) events.send(event)
            }
        }
        try {
            assertTrue(port.dispatchDropEnter(hwnd, textSource(java.util.concurrent.atomic.AtomicBoolean(false)), 1.0, 1.0))
            val entered = withTimeout(5.seconds) { assertIs<InputEvent.DropEntered>(events.receive()) }
            port.dispatchDropExited(hwnd)
            withTimeout(5.seconds) { assertIs<InputEvent.DropExited>(events.receive()) }
            val state = entered.offer.state.value
            assertEquals(DropOfferState.Terminated(DropOfferTerminationReason.LeftSurface), state)
            port.dispatchDropMoved(hwnd, 2.0, 2.0) // stale after exit
            port.dispatchDropPerformed(hwnd, 3.0, 3.0) // stale after exit
        } finally {
            collector.cancel()
            events.close()
        }
    }

    @Test
    fun unavailablePortKeepsWindowsUnobservedAndEnterInert() = runBlocking {
        val (port, driver) = newDriver(dropAvailable = false)
        val window = openWindow(driver, "drop-unavailable")
        val hwnd = port.lastTopLevelHwnd()
        assertTrue(port.dropObservers.isEmpty(), "no drop target installs when the port cannot register one")
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        assertFalse(port.dispatchDropEnter(hwnd, textSource(closed), 0.0, 0.0), "no callback answers without a registered target")
        assertFalse(closed.get(), "without a target no callback ever observes the source")
    }

    @Test
    fun windowCloseRevokesTheTargetBeforeDestroyingTheWindow() = runBlocking {
        val (port, driver) = newDriver(dropAvailable = true)
        val window = openWindow(driver, "drop-close")
        val hwnd = port.lastTopLevelHwnd()
        assertTrue(port.dropObservers.containsKey(hwnd))
        val closed = assertIs<KadreResult.Success<WindowCloseOutcome>>(window.close()).value
        assertIs<WindowCloseOutcome.Closed>(closed)
        assertFalse(port.dropObservers.containsKey(hwnd), "the native drop target is revoked before destroy")
        assertEquals(1, port.dropRevocations.get())
    }
}
