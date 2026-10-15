package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.contracts.driver.win32.Win32MessageInjector
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.interaction.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import java.util.concurrent.TimeUnit

/** Posted window messages prove the real queue/FFI/runtime path, not physical keyboard/IME entry. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32InputNativeContractTest {
    private suspend fun fixture(block: suspend (KffiWin32HostPort, Win32WindowRuntimeDriver, Window, Long, MutableList<InputEvent>) -> Unit) {
        val port = KffiWin32HostPort()
        val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter = org.graphiks.kadre.internal.runtime.RuntimeFailureReporter { failures += it })
        var collector: Job? = null
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec(title = "Kadre input proof"))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            val events = mutableListOf<InputEvent>()
            collector = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.collect { events += it }
            }
            block(port, driver, window, hwnd, events)
            assertTrue(failures.isEmpty(), "native callback failures: $failures")
        } finally { collector?.cancel(); driver.close(); port.close() }
    }

    private suspend fun pumpUntil(port: KffiWin32HostPort, description: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (!predicate() && System.nanoTime() < deadline) { port.drainOneMessage(10); yield() }
        assertTrue(predicate(), description)
    }

    private fun keyBits(scan: Int, extended: Boolean = false, release: Boolean = false, count: Int = 1): Long =
        count.toLong() or (scan.toLong() shl 16) or (if (extended) 0x01000000L else 0L) or
            (if (release) 0xc0000000L else 0L)
    private fun xy(x: Int, y: Int): Long = (x and 0xffff).toLong() or ((y and 0xffff).toLong() shl 16)
    private fun post(hwnd: Long, message: Int, wParam: Long = 0, lParam: Long = 0) {
        Win32MessageInjector().use { assertTrue(it.postWindowMessage(hwnd, message, wParam, lParam), "PostMessageW must queue input") }
    }

    @Test fun postedKeyboardPublishesPhysicalKeysModifiersRepeatAndRelease() = runBlocking {
        fixture { port, _, window, hwnd, events ->
            post(hwnd, 0x100, 0x11, keyBits(0x1d, extended = true))
            post(hwnd, 0x101, 0x11, keyBits(0x1d, extended = true, release = true))
            post(hwnd, 0x100, 0x41, keyBits(0x1e, count = 3))
            post(hwnd, 0x101, 0x41, keyBits(0x1e, release = true))
            pumpUntil(port, "six copied keyboard observations must cross the native queue") { events.filterIsInstance<InputEvent.Key>().size >= 6 }
            val keys = events.filterIsInstance<InputEvent.Key>()
            assertEquals(6, keys.size)
            assertEquals(PhysicalKey.Code(7, 0xe4), keys[0].physicalKey)
            assertEquals(KeyLocation.Right, keys[0].location)
            assertTrue(ModifierKey.Control in keys[0].modifiers.pressed)
            assertTrue(ModifierKey.Control !in keys[1].modifiers.pressed)
            assertTrue(keys.drop(2).all { it.physicalKey == PhysicalKey.Code(7, 4) })
            assertEquals(listOf(false, true, true, false), keys.drop(2).map { it.repeat })
            assertEquals(KeyState.Released, keys.last().keyState)
            assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty())
        }
    }

    @Test fun postedPointerAndWheelReachThePublicLogicalInputLane() = runBlocking {
        fixture { port, _, window, hwnd, events ->
            post(hwnd, 0x200, 0, xy(-10, -25))
            pumpUntil(port, "a posted negative client move must be observed") { events.any { it is InputEvent.PointerMoved } }
            val moved = events.filterIsInstance<InputEvent.PointerMoved>().first()
            val scale = window.surface.state.value.scaleFactor
            assertEquals(LogicalPoint(-10 / scale, -25 / scale), moved.position)
            post(hwnd, 0x20b, 2L shl 16, xy(-10, -25))
            post(hwnd, 0x20c, 2L shl 16, xy(-10, -25))
            val wheel = port.readWheelSettings()
            val lines = assertNotNull(wheel.verticalLines, "native wheel proof requires the OS line-scroll setting")
            val chars = assertNotNull(wheel.horizontalCharacters, "native wheel proof requires the OS character-scroll setting")
            assertTrue(lines > 0 && chars > 0, "native wheel proof requires both axes enabled")
            post(hwnd, 0x20a, 60L shl 16)
            post(hwnd, 0x20e, (-30 and 0xffff).toLong() shl 16)
            post(hwnd, 0x2a3)
            pumpUntil(port, "buttons, both wheel axes and leave must be observed") {
                events.filterIsInstance<InputEvent.Scrolled>().size >= 2 && events.any { it is InputEvent.PointerLeft } &&
                    events.filterIsInstance<InputEvent.PointerButtonChanged>().size >= 2
            }
            val buttons = events.filterIsInstance<InputEvent.PointerButtonChanged>()
            assertEquals(listOf(PointerButtonState.Pressed, PointerButtonState.Released), buttons.map { it.buttonState })
            assertTrue(buttons.all { it.button == PointerButton.Forward })
            val scroll = events.filterIsInstance<InputEvent.Scrolled>()
            assertEquals(ScrollDelta.Lines(0.0, -0.5 * lines), scroll[0].delta)
            assertEquals(ScrollDelta.Lines(-0.25 * chars, 0.0), scroll[1].delta)
        }
    }

    @Test fun postedFocusLossResetsHeldInputOnce() = runBlocking {
        fixture { port, _, window, hwnd, events ->
            post(hwnd, 0x7)
            post(hwnd, 0x100, 0x41, keyBits(0x1e))
            pumpUntil(port, "press must precede reset") { window.surface.input.state.value.keyboard.pressedKeys.isNotEmpty() }
            post(hwnd, WM_ACTIVATE, 1L shl 16)
            post(hwnd, 0x8)
            pumpUntil(port, "focus loss must reset") { events.any { it is InputEvent.StateReset } }
            repeat(10) { port.drainOneMessage(1) }
            assertEquals(1, events.filterIsInstance<InputEvent.StateReset>().size)
            assertEquals(InputStateResetReason.FocusLost, events.filterIsInstance<InputEvent.StateReset>().single().reason)
            assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty())
        }
    }

    @Test fun nativeKeyCallbackClosesBeforeAnyOrdinaryPressCanEscape() = runBlocking {
        fixture { port, _, window, hwnd, events ->
            val initial = window.surface.input.state.value
            window.surface.installInteractionHandler(InteractionHandler { _, _ -> runBlocking { window.close() } })
            post(hwnd, 0x100, 0x41, keyBits(0x1e))
            pumpUntil(port, "callback close must terminate the window") { window.state.value.phase == WindowPhase.Closed }
            assertEquals(initial, window.surface.input.state.value)
            assertTrue(events.none { it is InputEvent.Key })
        }
    }

    @Test fun nativeKeyCallbackCanOpenAnotherWindowWithoutLeavingAStalePressedKey() = runBlocking {
        fixture { port, driver, window, hwnd, events ->
            var opened = false
            window.surface.installInteractionHandler(InteractionHandler { context, _ ->
                assertIs<KadreResult.Success<*>>(context.request(InteractionAction.OpenWindow(WindowSpec(title = "reentrant native open"))))
                opened = true
            })
            post(hwnd, 0x7)
            post(hwnd, 0x100, 0x41, keyBits(0x1e))
            pumpUntil(port, "the synchronous key must open a second native window") { opened }
            assertEquals(2, driver.manager.state.value.windows.size)
            if (window.surface.state.value.focus == SurfaceFocus.Unfocused) {
                assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty(), "focus lost during the callback must not be followed by a stale ordinary press")
            } else assertEquals(1, events.filterIsInstance<InputEvent.Key>().size)
        }
    }

    @Test fun publicDesktopApplicationOpensFromAWorkerAndObservesNativeInput() {
        var observed: InputEvent.Key? = null
        var workerThread: Thread? = null
        val ownerThread = Thread.currentThread()
        val outcome = runKadreApplication(options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                withTimeout(6000) {
                    workerThread = Thread.currentThread()
                    val requested = assertIs<KadreResult.Success<WindowRequest>>(windows.requestWindow(WindowSpec(title = "Kadre public input proof"))).value
                    val window = assertIs<WindowRequestOutcome.OpenedHere>(requested.await()).window
                    val event = async(start = CoroutineStart.UNDISPATCHED) { window.surface.input.events.filterIsInstance<InputEvent.Key>().first() }
                    val borrowed = window.withDesktopHandle { handle ->
                        post((handle as DesktopNativeWindowHandle.Win32).hwnd.toLong(), 0x100, 0x41, keyBits(0x1e))
                    }
                    assertIs<KadreResult.Success<*>>(borrowed)
                    observed = event.await()
                    window.close()
                }
            })
        assertTrue(workerThread != ownerThread, "application coroutines must exercise worker-to-UI admission")
        assertEquals(PhysicalKey.Code(7, 4), assertNotNull(observed).physicalKey)
        assertTrue(outcome == SessionOutcome.Completed || outcome is SessionOutcome.Stopped, "session outcome: $outcome")
    }
}
