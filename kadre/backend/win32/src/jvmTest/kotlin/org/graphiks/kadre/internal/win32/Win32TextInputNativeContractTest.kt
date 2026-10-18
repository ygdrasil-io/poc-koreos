package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.contracts.driver.win32.Win32MessageInjector
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.managed.Win32Pointer
import org.graphiks.kffi.win32.managed.Win32TextException
import org.graphiks.kffi.win32.testsupport.Win32TextClient
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real HWND, TSF receiver and queue; these character packets do not prove physical IME entry. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32TextInputNativeContractTest {
    @Test fun realTextStoreReachesThePublicSessionAndRejectsStaleWrites() = runBlocking {
        fixture { port, window, hwnd ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val events = mutableListOf<TextInputEvent>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start=CoroutineStart.UNDISPATCHED) {
                session.events.collect { events += it }
            }
            try {
                assertEquals(1L, port.handleTextCharacter(hwnd, 0x109, 0xffff, 0))
                post(hwnd, 0x102, 0xd83d); post(hwnd, 0x102, 0xde00)
                pumpUntil(port, "one supplementary UTF-16 edit must reach the public session") { events.isNotEmpty() }
                val replace = assertIs<TextInputEvent.Replace>(events.single())
                assertEquals("😀", replace.text); assertEquals(TextRange(1, 1), replace.range)
                assertEquals(TextDocumentRevision(10), replace.baseRevision)
                post(hwnd, 0x286, 'x'.code.toLong())
                repeat(8) { port.drainOneMessage(1) }
                assertEquals(1, events.size, "compatibility IME characters cannot commit twice")
                assertEquals(KadreResult.Failure(KadreFailure.StaleRevision(10, 9)), session.updateCursor(
                    LogicalRect(LogicalPoint(10.0, 10.0), LogicalSize(1.0, 20.0)), TextDocumentRevision(9)))
            } finally { collector.cancel(); session.close() }
        }
    }

    @Test fun workerDocumentUpdateProgressesOnTheUiPump() = runBlocking {
        fixture { port, window, _ ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            try {
                val ownerThread = Thread.currentThread()
                val update = async(Dispatchers.Default) {
                    check(Thread.currentThread() !== ownerThread)
                    session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(11))
                }
                pumpUntil(port, "worker writeback must complete while the HWND thread pumps") { update.isCompleted }
                assertIs<KadreResult.Success<Unit>>(update.await())
                assertEquals(TextDocumentRevision(11), assertIs<TextInputState.Active>(session.state.value).documentRevision)
            } finally { session.close() }
        }
    }

    @Test fun realComCompositionAdoptsTheAcceptedRevisionAndEndsWithoutAReplace() = runBlocking {
        fixture { port, window, hwnd ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val events = mutableListOf<TextInputEvent>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start=CoroutineStart.UNDISPATCHED) { session.events.collect { events += it } }
            try { Win32TextClient(port.textServicesForTesting(), checkNotNull(port.textStoreForTesting(hwnd))).use { client ->
                client.startComposition("😀")
                val preedit = assertIs<TextInputEvent.CompositionChanged>(events.single())
                assertEquals(TextRange(1, 1), preedit.range); assertEquals("😀", preedit.text)
                assertEquals(TextDocumentRevision(10), preedit.baseRevision)
                applyOnWorker(port) { session.updateSurroundingText("a😀b", TextRange(3, 3), TextDocumentRevision(11)) }
                assertEquals(TextInputState.Active(TextDocumentRevision(11), TextRange(1, 3)), session.state.value)
                client.endComposition()
                val ended = assertIs<TextInputEvent.CompositionChanged>(events.last())
                assertNull(ended.range); assertEquals(TextDocumentRevision(11), ended.baseRevision)
                assertEquals(2, events.size); assertTrue(events.none { it is TextInputEvent.Replace })
            } } finally { collector.cancel(); session.close() }
        }
    }

    @Test fun nativeCloseDuringCompositionRevokesCallbacks() = runBlocking {
        fixture { port, window, hwnd ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val events = mutableListOf<TextInputEvent>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start=CoroutineStart.UNDISPATCHED) { session.events.collect { events += it } }
            try { Win32TextClient(port.textServicesForTesting(), checkNotNull(port.textStoreForTesting(hwnd))).use { client ->
                client.startComposition("😀")
                session.close()
                assertEquals(TextInputState.Closed, session.state.value)
                val replacement = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(
                    TextInputConfig(documentRevision=TextDocumentRevision(12)))).value
                try {
                    val cleanup = runCatching { client.endComposition() }.exceptionOrNull()
                    if (cleanup != null && cleanup !is Win32TextException) throw cleanup
                    repeat(8) { port.drainOneMessage(1) }
                    assertEquals(1, events.size)
                    assertEquals(TextInputState.Active(TextDocumentRevision(12), null), replacement.state.value)
                } finally { replacement.close() }
            } } finally { collector.cancel(); session.close() }
        }
    }

    @Test fun candidateRectTracksWindowMoveAndDpiReadback() = runBlocking {
        fixture { port, window, hwnd ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val logical = LogicalRect(LogicalPoint(10.25, 12.5), LogicalSize(1.5, 18.0))
            try { Win32TextClient(port.textServicesForTesting(), checkNotNull(port.textStoreForTesting(hwnd))).use { client ->
                applyOnWorker(port) { session.updateCursor(logical, TextDocumentRevision(10)) }
                fun expected() = win32CaretRect(logical, port.readScaleFactor(hwnd), Win32Pointer.clientToScreen(hwnd, 0, 0))
                assertEquals(expected(), client.caretRect())
                val before = client.caretRect()
                val bounds = port.readWindow(hwnd).outerBounds
                port.setWindowBounds(hwnd, PhysicalRect(PhysicalPoint(bounds.origin.x + 40, bounds.origin.y + 25), bounds.size), false)
                pumpUntil(port, "native layout notification must refresh the screen caret after moving") { client.caretRect() == expected() }
                assertNotEquals(before, client.caretRect())
                assertEquals(expected(), client.caretRect())
            } } finally { session.close() }
        }
    }

    @Test fun slowWritebackBoundsQueuedCharactersAndResumes() = runBlocking {
        fixture { port, window, hwnd ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val events = mutableListOf<TextInputEvent>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start=CoroutineStart.UNDISPATCHED) { session.events.collect { events += it } }
            try {
                post(hwnd, 0x102, 'x'.code.toLong()); post(hwnd, 0x102, 'y'.code.toLong())
                pumpUntil(port, "first edit must await application adoption") { events.size == 1 }
                repeat(8) { port.drainOneMessage(1) }
                assertEquals(1, events.size)
                applyOnWorker(port) { session.updateSurroundingText("axb", TextRange(2, 2), TextDocumentRevision(11)) }
                pumpUntil(port, "queued character must resume against the accepted revision") { events.size == 2 }
                val edits = events.map { assertIs<TextInputEvent.Replace>(it) }
                assertEquals(listOf("x", "y"), edits.map { it.text })
                assertEquals(listOf(TextDocumentRevision(10), TextDocumentRevision(11)), edits.map { it.baseRevision })
                assertEquals(TextRange(2, 2), edits.last().range)
            } finally { collector.cancel(); session.close() }
        }
    }

    /** Controls only the native filter decision; it does not stand in for installed-IME qualification. */
    @Test fun tsfConsumedKeysDoNotTranslateOrCommitTwice() = runBlocking {
        var target = 0L; var filtered = 0
        val controlled = object : KffiWin32HostPort() {
            override fun filterTextKey(hwnd: Long, message: Int, wParam: Long, lParam: Long): Boolean {
                if (hwnd != target || wParam != 0x41L) return super.filterTextKey(hwnd, message, wParam, lParam)
                assertNotNull(preparedKeyForTesting(), "layout-dependent key data must be copied before the filter")
                filtered++
                return true
            }
        }
        fixture(controlled) { port, window, hwnd ->
            target = hwnd
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(config())).value
            val keys = mutableListOf<InputEvent.Key>(); val text = mutableListOf<TextInputEvent>()
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val keyCollector = scope.launch(start=CoroutineStart.UNDISPATCHED) { window.surface.input.events.filterIsInstance<InputEvent.Key>().collect { keys += it } }
            val textCollector = scope.launch(start=CoroutineStart.UNDISPATCHED) { session.events.collect { text += it } }
            try {
                Win32MessageInjector().use { injector ->
                    check(injector.postWindowMessage(hwnd, 0x100, 0x41, 0x001e0001))
                    check(injector.postWindowMessage(hwnd, 0x101, 0x41, 0xc01e0001L))
                }
                pumpUntil(port, "both key packets must cross the pre-translation filter") { filtered == 2 }
                repeat(8) { port.drainOneMessage(1) }
                assertTrue(keys.isEmpty()); assertTrue(text.isEmpty())
                assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty())
            } finally { keyCollector.cancel(); textCollector.cancel(); session.close() }
        }
    }

    private suspend fun applyOnWorker(port: KffiWin32HostPort, block: suspend () -> KadreResult<Unit>) = coroutineScope {
        val update = async(Dispatchers.Default) { block() }
        pumpUntil(port, "application writeback must progress without blocking the UI thread") { update.isCompleted }
        assertIs<KadreResult.Success<Unit>>(update.await())
    }

    private suspend fun fixture(port: KffiWin32HostPort = KffiWin32HostPort(), block: suspend (KffiWin32HostPort, Window, Long) -> Unit) {
        val host = Win32HostSession.start(port)
        val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter=RuntimeFailureReporter { failures += it })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec(title="Kadre native text proof"))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            block(port, window, hwnd)
            assertTrue(failures.isEmpty(), "native text callback failure count=${failures.size}")
        } finally { driver.close(); host.close(); port.close() }
    }
    private suspend fun pumpUntil(port: KffiWin32HostPort, label: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) { port.drainOneMessage(10); yield() }
        assertTrue(done(), label)
    }
    private fun config() = TextInputConfig(surroundingText="ab", selection=TextRange(1, 1), documentRevision=TextDocumentRevision(10))
    private fun post(hwnd: Long, message: Int, wParam: Long) {
        Win32MessageInjector().use { check(it.postWindowMessage(hwnd, message, wParam, 1)) }
    }
}
