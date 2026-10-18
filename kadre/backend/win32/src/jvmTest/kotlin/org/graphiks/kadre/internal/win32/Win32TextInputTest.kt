package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.managed.*
import kotlin.test.*

class Win32TextInputTest {
    @Test fun queuedUpdateSeesTerminalFailureBeforeItsContinuationResumes() = runTest {
        fixture { native, _, window, _ ->
            val session=assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(TextInputConfig())).value
            var resumedState: TextInputState? = null
            var resumedCapability: Capability<Unit>? = null
            var reopened: KadreResult<TextInputSession>? = null
            val update=async(Dispatchers.Unconfined, start=CoroutineStart.UNDISPATCHED) {
                val result=session.updateSurroundingText("next", TextRange(4, 4), TextDocumentRevision(1))
                resumedState=session.state.value
                resumedCapability=window.surface.input.state.value.capabilities.textInput
                session.close()
                reopened=window.surface.input.openTextInput(TextInputConfig())
                result
            }
            assertFalse(update.isCompleted)
            native.failNow()
            assertIs<KadreResult.Failure>(update.await())
            assertIs<FeatureAvailability.Unavailable>(assertIs<Capability.Supported<Unit>>(resumedCapability).availability)
            assertEquals(TextInputState.Closed, resumedState)
            assertIs<KadreResult.Failure>(reopened)
        }
    }
    @Test fun nativeChangesUseOnlyTheAcceptedRevision() {
        val native = Native(); val events = mutableListOf<TextInputObservation>(); val failures = mutableListOf<KadreFailure>()
        val port = Win32TextInputPort(native)
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(command(events, failures))).value
        owner.activate()
        assertTrue(native.emit(Win32TextChange.Replace(Win32TextRange(1, 1), "😀", 10)))
        assertEquals(TextInputObservation.Replace(TextRange(1, 1), "😀", TextDocumentRevision(10)), events.single())
        assertFalse(native.emit(Win32TextChange.Selection(Win32TextRange(0, 0), 12)))
        native.drain()
        assertEquals(1, events.size); assertEquals(1, failures.size); assertEquals(1, native.owner.closed)
    }

    @Test fun newEpochWaitsForTheRuntimeAcknowledgment() = runTest {
        val native = Native(); val events = mutableListOf<TextInputObservation>(); val failures = mutableListOf<KadreFailure>()
        val budget = Budget(); val port = Win32TextInputPort(native)
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(command(events, failures, budget))).value
        owner.activate()
        native.owner.onApply = { assertTrue(native.emit(Win32TextChange.Replace(Win32TextRange(3, 3), "x", 11))) }
        val result = async(start=CoroutineStart.UNDISPATCHED) {
            port.updateDocument(TextInputDocumentCommand(owner, "a😀b", TextRange(3, 3), TextDocumentRevision(11)))
        }
        assertEquals(0, native.owner.writes)
        native.drain()
        assertIs<KadreResult.Success<Unit>>(result.await())
        assertTrue(events.isEmpty(), "native apply can publish before the common commit")
        assertTrue(budget.retained > 0)
        owner.documentApplied(TextDocumentRevision(11)); native.drain()
        assertEquals(TextDocumentRevision(11), events.single().baseRevision)
        assertTrue(failures.isEmpty())
        owner.close(); native.drain(); assertEquals(0L, budget.retained)
    }

    @Test fun staleCursorNeverTouchesNativeState() = runTest {
        val native = Native(); val port = Win32TextInputPort(native)
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(command())).value
        owner.activate()
        assertEquals(KadreResult.Failure(KadreFailure.StaleRevision(10, 9)), port.updateCursor(
            TextInputCursorCommand(owner, LogicalRect(LogicalPoint(1.0, 2.0), LogicalSize(1.0, 20.0)), TextDocumentRevision(9))))
        assertEquals(0, native.owner.cursorWrites)
        owner.close(); native.drain()
    }

    @Test fun pendingObservationsAreBoundedEvenWithoutTextPayload() = runTest {
        val native = Native(); val failures = mutableListOf<KadreFailure>(); val events = mutableListOf<TextInputObservation>()
        val budget = Budget(); val port = Win32TextInputPort(native)
        val config = command(events, failures, budget).copy(deliveryPolicy=KadrePolicies.Default.input.discreteEvents.copy(ingressCapacity=1))
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(config)).value
        owner.activate()
        native.owner.onApply = {
            assertTrue(native.emit(Win32TextChange.Selection(Win32TextRange(0, 0), 11)))
            assertFalse(native.emit(Win32TextChange.Selection(Win32TextRange(1, 1), 11)))
        }
        val result = async(start=CoroutineStart.UNDISPATCHED) {
            port.updateDocument(TextInputDocumentCommand(owner, "ab", TextRange(1, 1), TextDocumentRevision(11)))
        }
        native.drain()
        assertIs<KadreResult.Failure>(result.await())
        assertTrue(events.isEmpty()); assertEquals(1, failures.size)
        assertEquals(1, native.owner.closed); assertEquals(0L, budget.retained)
    }

    @Test fun cancellingAQueuedDocumentReturnsItsBytesWithoutWritingNativeState() = runTest {
        val native = Native(); val budget = Budget(); val port = Win32TextInputPort(native)
        val failures = mutableListOf<KadreFailure>()
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(command(failures=failures, budget=budget))).value
        owner.activate()
        val update = async(start=CoroutineStart.UNDISPATCHED) {
            port.updateDocument(TextInputDocumentCommand(owner, "next", TextRange(4, 4), TextDocumentRevision(11)))
        }
        assertTrue(budget.retained > 0)
        update.cancelAndJoin()
        assertTrue(native.owner.revoked, "cancellation must revoke before the queued UI work can run")
        native.drain()
        assertEquals(0, native.owner.writes); assertEquals(1, native.owner.closed)
        assertEquals(0L, budget.retained); assertEquals(1, failures.size)
    }

    @Test fun nativeFailureDuringOpenClosesTheReturnedOwner() {
        val native = Native(); val failures = mutableListOf<KadreFailure>()
        native.failDuringOpen = true
        assertIs<KadreResult.Failure>(Win32TextInputPort(native).open(command(failures=failures)))
        native.drain()
        assertEquals(1, failures.size); assertEquals(1, native.owner.closed)
        assertEquals(0, native.owner.activations)
    }

    @Test fun allTextConfigurationVariantsHaveAnExplicitMapping() {
        assertEquals(Win32TextPurpose.entries.toSet(), TextInputPurpose.entries.map {
            win32TextConfiguration(TextInputConfig(purpose=it)).purpose }.toSet())
        assertEquals(Win32TextAction.entries.toSet(), TextInputAction.entries.map {
            win32TextConfiguration(TextInputConfig(action=it)).action }.toSet())
        assertEquals(Win32TextCapitalization.entries.toSet(), TextCapitalization.entries.map {
            win32TextConfiguration(TextInputConfig(capitalization=it)).capitalization }.toSet())
        val configuration = win32TextConfiguration(TextInputConfig(autocorrect=false, multiline=true))
        assertFalse(configuration.autocorrect); assertTrue(configuration.multiline)
    }

    @Test fun supplementaryCharacterCompositionRebasesFrom10To11() = runTest {
        fixture { native, _, window, _ ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(
                TextInputConfig(surroundingText="ab", selection=TextRange(1, 1), documentRevision=TextDocumentRevision(10)))) .value
            val events = async(start=CoroutineStart.UNDISPATCHED) { session.events.take(2).toList() }
            assertTrue(native.emit(Win32TextChange.Composition(Win32TextRange(1, 1), "😀", Win32TextRange(0, 2), 10)))
            native.owner.onApply = {
                assertTrue(native.emit(Win32TextChange.Composition(Win32TextRange(1, 3), "😀", Win32TextRange(0, 2), 11)))
            }
            val update = async(start=CoroutineStart.UNDISPATCHED) {
                session.updateSurroundingText("a😀b", TextRange(3, 3), TextDocumentRevision(11))
            }
            native.drain(); assertIs<KadreResult.Success<Unit>>(update.await()); native.drain()
            assertEquals(TextInputState.Active(TextDocumentRevision(11), TextRange(1, 3)), session.state.value)
            val observed = events.await().map { assertIs<TextInputEvent.CompositionChanged>(it) }
            assertEquals(listOf(TextDocumentRevision(10), TextDocumentRevision(11)), observed.map { it.baseRevision })
            assertTrue(observed.all { it.text == "😀" && it.selection == TextRange(0, 2) })
            session.close()
        }
    }

    @Test fun focusSuspendsCompositionUntilTheHostEndsIt() = runTest {
        fixture { native, host, window, hwnd ->
            host.dispatchWindowMessage(hwnd, 0x7)
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(
                TextInputConfig(surroundingText="ab", selection=TextRange(1, 1), documentRevision=TextDocumentRevision(10)))).value
            native.emit(Win32TextChange.Composition(Win32TextRange(1, 1), "😀", Win32TextRange(0, 2), 10))
            host.dispatchWindowMessage(hwnd, 0x8)
            assertEquals(TextInputState.Suspended(TextDocumentRevision(10), TextRange(1, 1)), session.state.value)
            native.emit(Win32TextChange.Composition(null, "", null, 10))
            assertEquals(TextInputState.Suspended(TextDocumentRevision(10), null), session.state.value)
            host.dispatchWindowMessage(hwnd, 0x7)
            assertEquals(TextInputState.Active(TextDocumentRevision(10), null), session.state.value)
            session.close()
        }
    }

    @Test fun openingOnAnUnfocusedSurfaceStartsSuspended() = runTest {
        fixture(initiallyFocused=false) { _, host, window, hwnd ->
            assertEquals(SurfaceFocus.Unfocused, window.surface.state.value.focus)
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(TextInputConfig())).value
            try {
                assertEquals(TextInputState.Suspended(TextDocumentRevision(0), null), session.state.value)
                host.dispatchWindowMessage(hwnd, 0x7)
                assertEquals(TextInputState.Active(TextDocumentRevision(0), null), session.state.value)
            } finally { session.close() }
        }
    }

    @Test fun focusKnownBeforeOpenIsAppliedAfterNativeActivation() {
        val native = Native(); val port = Win32TextInputPort(native)
        port.setFocused(false)
        val owner = assertIs<KadreResult.Success<TextInputOwner>>(port.open(command())).value
        try { owner.activate(); assertEquals(false, native.owner.lastFocus) }
        finally { owner.close(); native.drain() }
    }

    @Test fun failedTextInitializationLeavesKeyboardAndPointerInstalled() = runTest {
        fixture(failInitialization=true) { _, host, window, hwnd ->
            val capabilities = window.surface.input.state.value.capabilities
            assertIs<FeatureAvailability.Unavailable>(assertIs<Capability.Supported<Unit>>(capabilities.textInput).availability)
            assertEquals(FeatureAvailability.Available, capabilities.keyboard)
            assertEquals(FeatureAvailability.Available, capabilities.pointer)
            assertIs<KadreResult.Failure>(window.surface.input.openTextInput(TextInputConfig()))
            val reason = assertIs<KadreFailure.PlatformFailure>(assertIs<FeatureAvailability.Unavailable>(assertIs<Capability.Supported<Unit>>(capabilities.textInput).availability).failure)
            assertTrue(reason.message.contains("80004005"), "native initialization must retain a sanitized HRESULT")
            host.dispatchWindowMessage(hwnd, 0x200)
            assertEquals(1, window.surface.input.state.value.pointers.size)
        }
    }

    @Test fun windowCloseRetiresTextBeforeDestroyingTheWindow() = runTest {
        fixture { native, _, window, _ ->
            val session = assertIs<KadreResult.Success<TextInputSession>>(window.surface.input.openTextInput(TextInputConfig())).value
            window.close()
            assertEquals(TextInputState.Closed, session.state.value)
            assertEquals(true to 1, native.atDestroy)
        }
    }

    private suspend fun fixture(failInitialization: Boolean = false, initiallyFocused: Boolean = true,
        block: suspend (Native, DeterministicWin32HostPort, Window, Long) -> Unit) {
        val native = Native(); val text = Win32TextInputPort(native)
        val host = object : DeterministicWin32HostPort() {
            override fun createTextInputPort(hwnd: Long): TextInputPort {
                if (failInitialization) throw Win32TextException(0x80004005.toInt())
                return text
            }
            override fun setTextFocus(hwnd: Long, focused: Boolean) { text.setFocused(focused) }
            override fun closeTextInput(hwnd: Long) { text.close() }
            override fun destroyWindow(hwnd: Long) {
                native.atDestroy = native.owner.revoked to native.owner.closed
                super.destroyWindow(hwnd)
            }
        }
        val driver = Win32WindowRuntimeDriverFactory.create(host, KadrePolicies.Default.resources)
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec())).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            if (initiallyFocused) host.dispatchWindowMessage(host.lastTopLevelHwnd(), 0x7)
            block(native, host, window, host.lastTopLevelHwnd())
        } finally { driver.close(); native.drain(); host.close() }
    }

    @Test fun caretPixelsCoverFractionalLogicalBoundsAndRejectOverflow() {
        assertEquals(Win32WindowRect(-99, 53, 2, 26), win32CaretRect(
            LogicalRect(LogicalPoint(1.2, 2.4), LogicalSize(0.8, 20.1)), 1.25, Win32Point(-100, 50)))
        assertFailsWith<IllegalArgumentException> { win32CaretRect(
            LogicalRect(LogicalPoint(Int.MAX_VALUE.toDouble(), 0.0), LogicalSize(1.0, 10.0)), 2.0, Win32Point(0, 0)) }
    }

    private fun command(events: MutableList<TextInputObservation> = mutableListOf(),
        failures: MutableList<KadreFailure> = mutableListOf(), budget: RuntimePayloadBudget = Budget()) = TextInputOpenCommand(
        SurfaceId(1), TextInputConfig(surroundingText="ab", selection=TextRange(1, 1), documentRevision=TextDocumentRevision(10)),
        { events += it; true }, budget, { failures += it })

    private class Budget : RuntimePayloadBudget {
        var retained = 0L
        override fun retain(bytes: Long): KadreResult<AutoCloseable> {
            retained += bytes; var released = false
            return KadreResult.Success(AutoCloseable { check(!released); released=true; retained -= bytes })
        }
    }

    private class Native : Win32TextNativePort {
        override val capability: Capability<Unit> = Capability.Supported(Unit, FeatureAvailability.Available)
        val owner = NativeOwner(); var failDuringOpen = false
        var atDestroy: Pair<Boolean, Int>? = null
        private val queue = ArrayDeque<() -> Unit>()
        private var receiver: (Win32TextChange) -> Boolean = { false }
        private var failureReceiver: (Throwable) -> Unit = {}
        fun failNow() { failureReceiver(Win32TextException(0x80004005.toInt())) }
        fun emit(change: Win32TextChange) = receiver(change)
        fun drain() { var turns=0; while (queue.isNotEmpty()) { check(turns++ < 100); queue.removeFirst()() } }
        override fun <T> onMainThread(block: () -> T): T = block()
        override fun postOnMainThread(block: () -> Unit, onFailure: (Throwable) -> Unit) { queue += { try { block() } catch (cause: Throwable) { onFailure(cause) } } }
        override fun createStore(initial: Win32TextSnapshot, configuration: Win32TextConfiguration, limits: Win32TextLimits,
            onChange: (Win32TextChange) -> Boolean, onFailure: (Throwable) -> Unit): Win32NativeTextOwner {
            receiver=onChange; failureReceiver=onFailure
            if (failDuringOpen) onFailure(Win32TextException(0x80004005.toInt()))
            return owner
        }
    }

    private class NativeOwner : Win32NativeTextOwner {
        var closed=0; var activations=0; var writes=0; var cursorWrites=0; var revoked=false
        var onApply: () -> Unit = {}
        var lastFocus: Boolean? = null
        override fun activate() { activations++ }
        override fun apply(snapshot: Win32TextSnapshot) { writes++; onApply() }
        override fun updateCursor(rect: LogicalRect, epoch: Long) { cursorWrites++ }
        override fun setFocused(focused: Boolean) { lastFocus = focused }
        override fun handleCharacter(message: Int, wParam: Long, lParam: Long): Long? = null
        override fun revoke() { revoked=true }
        override fun close() { closed++ }
    }
}
