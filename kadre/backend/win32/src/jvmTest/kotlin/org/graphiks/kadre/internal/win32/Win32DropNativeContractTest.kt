package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.managed.Win32Pointer
import org.graphiks.kffi.win32.testsupport.Win32DropDataObject
import org.graphiks.kffi.win32.testsupport.Win32DropProbe

/**
 * Real HWND, real RegisterDragDrop and a real OLE invocation of the registered
 * IDropTarget with genuine HGLOBAL media (KFFI native test support). A direct
 * COM call exercises the same frontier Windows uses; an inter-application
 * physical drag stays a manual reception and is never claimed here.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32DropNativeContractTest {
    @Test fun realOleTargetRoutesAnAcceptedDropToThePublicTransfer() = runBlocking {
        fixture { port, window, hwnd ->
            window.surface.installInteractionHandler(InteractionHandler { context, event ->
                if (event is InteractionEvent.DropEntered) context.request(InteractionAction.AcceptDrop(event.offer.id))
            })
            val registered = Win32DropProbe.registeredTarget(hwnd)
            check(registered != 0L) { "the driver must register a real OLE drop target" }

            val events = mutableListOf<InputEvent>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.collect { event ->
                    if (event is InputEvent.DropEntered || event is InputEvent.DropMoved ||
                        event is InputEvent.Dropped) events += event
                }
            }
            Win32DropDataObject(listOf("C:\\kadre-drop.txt"), "dropped through OLE").use { data ->
                assertEquals(1, Win32DropProbe.dragEnter(registered, data, keyState = 8, x = 220, y = 140, allowedEffects = 7),
                    "an accepted drag enter answers DROPEFFECT_COPY")
                assertEquals(1, Win32DropProbe.dragOver(registered, keyState = 2, x = 260, y = 180, allowedEffects = 7))
                assertEquals(1, Win32DropProbe.drop(registered, data, keyState = 0, x = 280, y = 200, allowedEffects = 7))

                val entered = assertIs<InputEvent.DropEntered>(events[0])
                val moved = assertIs<InputEvent.DropMoved>(events[1])
                val dropped = assertIs<InputEvent.Dropped>(events[2])
                assertEquals(entered.offer.id, moved.offerId)
                val expected = Win32Pointer.screenToClient(hwnd, 220, 140)
                val scale = port.readScaleFactor(hwnd)
                assertEquals(expected.x / scale, entered.position.x)
                assertEquals(expected.y / scale, entered.position.y)
                assertEquals(DropOfferState.TransferAvailable, dropped.offer.state.value)

                val transfer = assertIs<KadreResult.Success<DropTransfer>>(dropped.offer.claimTransfer()).value
                try {
                    assertEquals(2, transfer.items.size, "one file item and one text item from the real media")
                    val text = transfer.items.single { it.descriptor.kind == DropItemKind.Text }
                    val bytes = StringBuilder()
                    text.collectBytes(64) { bytes.append(String(it, Charsets.UTF_8)) }
                    assertEquals("dropped through OLE", bytes.toString())
                    val file = transfer.items.single { it.descriptor.kind == DropItemKind.File }
                    assertEquals("kadre-drop.txt", file.descriptor.displayName)
                    assertEquals(DropItemReadMode.Replayable, file.readMode)
                } finally {
                    transfer.close()
                }
            }
            collector.cancel()
        }
    }

    @Test fun refusedAdmissionAnswersNoneAndTerminatesTheOfferAsRejected() = runBlocking {
        fixture { port, window, hwnd ->
            // No interaction handler: the synchronous admission cannot accept.
            val registered = Win32DropProbe.registeredTarget(hwnd)
            check(registered != 0L)
            val entered = CompletableDeferred<InputEvent.DropEntered>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.filterIsInstance<InputEvent.DropEntered>().collect { entered.complete(it) }
            }
            Win32DropDataObject(listOf("C:\\refused.txt"), "no taker").use { data ->
                assertEquals(0, Win32DropProbe.dragEnter(registered, data, keyState = 8, x = 10, y = 10, allowedEffects = 7),
                    "a refused admission answers DROPEFFECT_NONE")
                val event = withTimeout(5_000) { entered.await() }
                assertEquals(DropOfferState.Terminated(DropOfferTerminationReason.Rejected), event.offer.state.value)
            }
            collector.cancel()
        }
    }

    @Test fun targetCloseRevokesTheRegistrationBeforeTheWindowIsDestroyed() = runBlocking {
        fixture { port, window, hwnd ->
            check(Win32DropProbe.registeredTarget(hwnd) != 0L)
            val closed = assertIs<KadreResult.Success<WindowCloseOutcome>>(window.close()).value
            assertIs<WindowCloseOutcome.Closed>(closed)
            assertEquals(0L, Win32DropProbe.registeredTarget(hwnd), "RevokeDragDrop must run before DestroyWindow")
        }
    }

    private suspend fun fixture(block: suspend (KffiWin32HostPort, Window, Long) -> Unit) {
        val port = KffiWin32HostPort()
        val host = Win32HostSession.start(port)
        val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter = RuntimeFailureReporter { failures += it })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec(title = "Kadre native drop proof"))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            block(port, window, hwnd)
            assertTrue(failures.isEmpty(), "native drop callback failure count=${failures.size}: $failures")
        } finally {
            driver.close(); host.close(); port.close()
        }
    }
}
