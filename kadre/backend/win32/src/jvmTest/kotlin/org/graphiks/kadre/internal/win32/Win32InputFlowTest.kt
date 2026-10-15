package org.graphiks.kadre.internal.win32

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*

@OptIn(DelicateKadreApi::class)
class Win32InputFlowTest {
    private suspend fun fixture(block: suspend (DeterministicWin32HostPort, Win32WindowRuntimeDriver, Window, Long) -> Unit) {
        val port = object : DeterministicWin32HostPort() {
            override fun readClientPhysicalSize(hwnd: Long) = 1000 to 750
            override fun readScaleFactor(hwnd: Long) = 1.25
        }
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources)
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec())).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            block(port, driver, window, port.lastTopLevelHwnd())
        } finally { driver.close(); port.close() }
    }

    private fun key(pressed: Boolean = true, repeat: Boolean = false, count: Int = 1, text: String? = "€") =
        Win32KeyInput(0x45, 0x12, false, pressed, repeat, count,
            KeyboardModifiers(setOf(ModifierKey.Control, ModifierKey.Alt)), text, false, 1L)
    private fun xy(x: Int, y: Int): Long = (x and 0xffff).toLong() or ((y and 0xffff).toLong() shl 16)

    @Test fun installedObserversAndInitialMetricsDescribeTheRealWindow() = runBlocking {
        fixture { _, _, window, _ ->
            assertEquals(FeatureAvailability.Available, window.surface.input.state.value.capabilities.keyboard)
            assertEquals(FeatureAvailability.Available, window.surface.input.state.value.capabilities.pointer)
            assertEquals(1.25, window.surface.state.value.scaleFactor)
            assertEquals(PhysicalSize(1000, 750), window.surface.state.value.physicalSize)
            assertEquals(LogicalSize(800.0, 600.0), window.state.value.contentSize)
        }
    }

    @Test fun keyboardRepeatAndLayoutChangeKeepOnePhysicalPressAndLogicalRelease() = runBlocking {
        fixture { port, _, window, hwnd ->
            val events = async(start = CoroutineStart.UNDISPATCHED) { window.surface.input.events.take(4).toList() }
            port.keyInput = key(count = 3)
            port.dispatchWindowMessage(hwnd, 0x100)
            assertEquals(setOf(PhysicalKey.Code(7, 8)), window.surface.input.state.value.keyboard.pressedKeys)
            port.keyInput = key(pressed = false, text = "x").copy(layoutId = 2L)
            port.dispatchWindowMessage(hwnd, 0x101)
            val keys = withTimeout(2000) { events.await() }.map { assertIs<InputEvent.Key>(it) }
            assertEquals(listOf(false, true, true, false), keys.map { it.repeat })
            assertTrue(keys.all { it.logicalKey == LogicalKey.Character("€") })
            assertTrue(keys.all { it.modifiers.pressed == setOf(ModifierKey.Control, ModifierKey.Alt) })
            assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty())
            assertTrue(keys.zipWithNext().all { (a, b) -> b.stamp != a.stamp })
        }
    }

    @Test fun signedPointerMotionButtonsAndLeaveUseWindowDpiAndRearm() = runBlocking {
        fixture { port, _, window, hwnd ->
            val events = async(start = CoroutineStart.UNDISPATCHED) { window.surface.input.events.take(5).toList() }
            port.dispatchWindowMessage(hwnd, 0x200, 0, xy(-10, -25))
            yield()
            port.dispatchWindowMessage(hwnd, 0x200, 0, xy(0, 5))
            yield()
            assertEquals(1L, port.dispatchWindowMessage(hwnd, 0x20b, 1L shl 16, xy(0, 5)))
            port.dispatchWindowMessage(hwnd, 0x2a3)
            val observed = withTimeout(2000) { events.await() }
            assertEquals(LogicalPoint(-8.0, -20.0), assertIs<InputEvent.PointerEntered>(observed[0]).position)
            assertEquals(LogicalDelta(0.0, 0.0), assertIs<InputEvent.PointerMoved>(observed[1]).delta)
            assertEquals(LogicalDelta(8.0, 24.0), assertIs<InputEvent.PointerMoved>(observed[2]).delta)
            assertEquals(PointerButton.Back, assertIs<InputEvent.PointerButtonChanged>(observed[3]).button)
            assertIs<InputEvent.PointerLeft>(observed[4])
            port.dispatchWindowMessage(hwnd, 0x200, 0, xy(0, 5))
            assertEquals(2, port.leaveArms)
        }
    }

    @Test fun wheelUsesSystemSettingsAndDoesNotInventPageUnits() = runBlocking {
        fixture { port, _, window, hwnd ->
            val events = async(start = CoroutineStart.UNDISPATCHED) { window.surface.input.events.take(2).toList() }
            port.wheelSettings = Win32WheelSettings(3, 2)
            port.dispatchWindowMessage(hwnd, 0x20a, 60L shl 16, xy(-100, -100))
            port.dispatchWindowMessage(hwnd, 0x20e, (-30 and 0xffff).toLong() shl 16, xy(-100, -100))
            val observed = withTimeout(2000) { events.await() }.map { assertIs<InputEvent.Scrolled>(it) }
            assertEquals(ScrollDelta.Lines(0.0, -1.5), observed[0].delta)
            assertEquals(ScrollDelta.Lines(-0.5, 0.0), observed[1].delta)
            val revision = window.surface.input.state.value.revision
            port.wheelSettings = Win32WheelSettings(null, 0)
            port.dispatchWindowMessage(hwnd, 0x20a, 120L shl 16)
            port.dispatchWindowMessage(hwnd, 0x20e, 120L shl 16)
            assertEquals(revision, window.surface.input.state.value.revision)
        }
    }

    @Test fun focusLossResetsOnceAndCloseFreezesTheLastInputState() = runBlocking {
        fixture { port, _, window, hwnd ->
            port.dispatchWindowMessage(hwnd, 0x7)
            port.keyInput = key()
            port.dispatchWindowMessage(hwnd, 0x100)
            port.dispatchWindowMessage(hwnd, 0x201, 1, xy(1, 2))
            val resets = mutableListOf<InputEvent.StateReset>()
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.filterIsInstance<InputEvent.StateReset>().collect { resets += it }
            }
            port.dispatchWindowMessage(hwnd, WM_ACTIVATE, 1L shl 16) // high word minimized, LOWORD inactive
            port.dispatchWindowMessage(hwnd, 0x8)
            yield()
            assertEquals(1, resets.size)
            assertTrue(window.surface.input.state.value.keyboard.pressedKeys.isEmpty())
            assertTrue(window.surface.input.state.value.pointers.all { it.pressedButtons.isEmpty() })
            port.dispatchWindowMessage(hwnd, 0x7)
            port.dispatchWindowMessage(hwnd, 0x100)
            val final = window.surface.input.state.value
            window.close()
            port.dispatchWindowMessage(hwnd, 0x101)
            assertEquals(final, window.surface.input.state.value)
            collecting.cancelAndJoin()
        }
    }

    @Test fun unhandledReturnAndSystemKeyDefaultsFollowTheEffectivePolicy() = runBlocking {
        fixture { port, _, window, hwnd ->
            port.defaultResult = 87L
            assertEquals(87L, port.dispatchWindowMessage(hwnd, 0x400))
            port.keyInput = key()
            assertEquals(87L, port.dispatchWindowMessage(hwnd, 0x104))
            val before = port.defWindowProcCalls.get()
            assertIs<KadreResult.Success<*>>(window.surface.apply(SurfaceUpdate(
                inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible))))
            assertEquals(InputDefaultBehavior.SuppressWhenPossible, window.surface.state.value.inputDefaultBehavior)
            assertEquals(0L, port.dispatchWindowMessage(hwnd, 0x104))
            assertEquals(0L, port.dispatchWindowMessage(hwnd, 0x106)) // system char is default policy only, no text session
            assertEquals(before, port.defWindowProcCalls.get())
            assertIs<KadreResult.Success<*>>(window.surface.apply(SurfaceUpdate(
                inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault))))
            assertEquals(87L, port.dispatchWindowMessage(hwnd, 0x104))
        }
    }

    @Test fun synchronousKeyCanOpenAnotherWindowWithoutDuplicatingTheOrdinaryEvent() = runBlocking {
        fixture { port, driver, window, hwnd ->
            var callbacks = 0
            val registration = assertIs<KadreResult.Success<org.graphiks.kadre.interaction.InteractionRegistration>>(
                window.surface.installInteractionHandler(org.graphiks.kadre.interaction.InteractionHandler { context, _ ->
                    callbacks++
                    assertIs<KadreResult.Success<*>>(context.request(org.graphiks.kadre.interaction.InteractionAction.OpenWindow()))
                })).value
            val event = async(start = CoroutineStart.UNDISPATCHED) { window.surface.input.events.first() }
            port.keyInput = key()
            port.dispatchWindowMessage(hwnd, 0x100)
            assertEquals(2, driver.manager.state.value.windows.size)
            assertEquals(1, callbacks)
            assertEquals(LogicalKey.Character("€"), assertIs<InputEvent.Key>(withTimeout(2000) { event.await() }).logicalKey)
            registration.close()
        }
    }

    @Test fun closingInsideSynchronousCallbackPreventsTheFollowingOrdinaryPress() = runBlocking {
        fixture { port, _, window, hwnd ->
            val original = window.surface.input.state.value
            window.surface.installInteractionHandler(org.graphiks.kadre.interaction.InteractionHandler { _, _ ->
                runBlocking { window.close() }
            })
            port.keyInput = key()
            port.dispatchWindowMessage(hwnd, 0x100)
            assertEquals(original, window.surface.input.state.value)
        }
    }
}


