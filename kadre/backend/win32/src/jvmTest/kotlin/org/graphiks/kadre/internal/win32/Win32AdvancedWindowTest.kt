package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.PointerButton
import org.graphiks.kadre.interaction.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import kotlin.test.*

@OptIn(DelicateKadreApi::class)
class Win32AdvancedWindowTest {
    private suspend fun fixture(block: suspend (DeterministicWin32HostPort, Window, Long) -> Unit) {
        val port = DeterministicWin32HostPort()
        Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources).use { driver ->
            val window = assertIs<WindowRequestOutcome.OpenedHere>(assertIs<KadreResult.Success<WindowRequest>>(
                driver.manager.requestWindow()).value.await()).window
            block(port, window, port.lastTopLevelHwnd())
        }
    }
    private fun image() = BinaryImage(ByteArray(16) { 0x7f }, ImageFormat.Rgba8, PhysicalSize(2, 2))

    @Test fun nativePropertiesExposeOnlyExactLevelsAndRoundTripImagesAndAffinity() = runBlocking { fixture { _, window, _ ->
        assertEquals(setOf(WindowLevel.Normal, WindowLevel.Floating), assertIs<Capability.Supported<Set<WindowLevel>>>(window.capabilities.value.level).constraints)
        val icon = image()
        val applied = assertIs<WindowUpdateOutcome.Applied>(assertIs<KadreResult.Success<WindowUpdateOutcome>>(
            window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating), icon = PropertyChange.Set(icon), contentProtection = PropertyChange.Set(true)))).value)
        assertEquals(WindowLevel.Floating, applied.state.level); assertEquals(icon, applied.state.icon); assertTrue(applied.state.contentProtection)
        window.apply(WindowUpdate(icon = PropertyChange.Clear, level = PropertyChange.Set(WindowLevel.Normal), contentProtection = PropertyChange.Set(false)))
        assertNull(window.state.value.icon); assertEquals(WindowLevel.Normal, window.state.value.level); assertFalse(window.state.value.contentProtection)
    } }

    @Test fun cursorPolicyAndConfinementRevokeWithoutInventingFocusLoss() = runBlocking { fixture { port, window, hwnd ->
        port.dispatchWindowMessage(hwnd, 0x7)
        val cursor = CursorStyle.Custom(CursorImage(image(), PhysicalPoint(1, 0)))
        assertIs<SurfaceUpdateOutcome.Applied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(
            cursor = PropertyChange.Set(cursor), pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))).value)
        assertEquals(cursor, window.surface.state.value.cursor)
        port.dispatchWindowMessage(hwnd, 0x001f) // WM_CANCELMODE: release without synthesizing focus loss
        assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
        assertEquals(SurfaceFocus.Focused, window.surface.state.value.focus)
        window.surface.apply(SurfaceUpdate(cursor = PropertyChange.Set(CursorStyle.Hidden)))
        assertEquals(1L, port.dispatchWindowMessage(hwnd, 0x20, lParam = 1L))
        port.defaultResult = 91L
        assertEquals(91L, port.dispatchWindowMessage(hwnd, 0x20, lParam = 2L), "non-client cursor keeps the OS policy")
    } }

    @Test fun phaseFiveUnsupportedVariantsAreQualifiedWithoutApproximation() = runBlocking { fixture { _, window, _ ->
        val mixed = assertIs<WindowUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<WindowUpdateOutcome>>(window.apply(WindowUpdate(
            title = PropertyChange.Set("kept"), level = PropertyChange.Set(WindowLevel.Modal), transparency = PropertyChange.Set(true), blurBehind = PropertyChange.Set(true)))).value)
        assertEquals(setOf(WindowProperty.Level, WindowProperty.Transparency, WindowProperty.Blur), mixed.rejected.map { it.field }.toSet())
        assertEquals("kept", mixed.state.title); assertEquals(WindowLevel.Normal, mixed.state.level)
        val surface = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(
            cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Grab)), hitTesting = PropertyChange.Set(HitTestingMode.Disabled), pointerCapture = PropertyChange.Set(PointerCaptureMode.Locked)))).value)
        assertEquals(3, surface.rejected.size)
        assertIs<Capability.Unsupported>(window.surface.input.state.value.capabilities.rawInput)
    } }

    @Test fun moveAndResizeAreOnlyAdmittedInsideALivePrimaryPointerCallback() = runBlocking { fixture { port, window, hwnd ->
        val kinds = assertIs<Capability.Supported<Set<InteractionKind>>>(window.surface.capabilities.value.handlerInteractions).constraints
        assertTrue(InteractionKind.BeginWindowMove in kinds && InteractionKind.BeginWindowResize in kinds)
        var action: InteractionAction = InteractionAction.BeginWindowMove
        var outcome: KadreResult<InteractionRequestId>? = null
        val registration = assertIs<KadreResult.Success<InteractionRegistration>>(window.surface.installInteractionHandler(InteractionHandler { context, _ -> outcome = context.request(action) })).value
        val outcomes = mutableListOf<InteractionActionOutcome>()
        val collecting = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { registration.outcomes.collect { outcomes += it } }
        port.dispatchWindowMessage(hwnd, 0x204, lParam = 10L or (20L shl 16))
        assertIs<InteractionActionOutcome.Rejected>(outcomes.last()); assertTrue(port.nativeDrags.isEmpty())
        port.dispatchWindowMessage(hwnd, 0x7)
        port.scriptedScaleFactor[hwnd] = 1.5
        port.dispatchWindowMessage(hwnd, 0x201, lParam = 10L or (20L shl 16))
        assertIs<KadreResult.Success<InteractionRequestId>>(outcome)
        assertEquals(Triple(2, 10, 20), port.nativeDrags.single())
        assertTrue(window.surface.input.state.value.pointers.all { PointerButton.Primary !in it.pressedButtons }, "the press consumed by the modal drag is not reinserted after it ends")
        action = InteractionAction.BeginWindowResize(ResizeEdge.SouthEast)
        port.dispatchWindowMessage(hwnd, 0x201)
        assertEquals(17, port.nativeDrags.last().first)
        window.apply(WindowUpdate(resizable = PropertyChange.Set(false)))
        port.dispatchWindowMessage(hwnd, 0x201)
        assertIs<InteractionActionOutcome.Rejected>(outcomes.last()); assertEquals(2, port.nativeDrags.size)
        collecting.cancel(); registration.close()
    } }

    @Test fun attentionIsWindowOwnedAndStopsAtClose() = runBlocking { fixture { _, window, _ ->
        assertIs<KadreResult.Success<Unit>>(window.requestAttention(WindowAttention.Informational))
        assertIs<KadreResult.Success<Unit>>(window.requestAttention(WindowAttention.Critical))
        assertIs<KadreResult.Success<Unit>>(window.requestAttention(WindowAttention.None))
        window.close()
        assertIs<KadreResult.Failure>(window.requestAttention(WindowAttention.Critical))
    } }

    @Test fun confinementLossRevokesTheLeaseAndNeverReacquiresOnFocusRegain() = runBlocking { fixture { port, window, hwnd ->
        suspend fun acquire() {
            port.dispatchWindowMessage(hwnd, 0x7)
            assertIs<SurfaceUpdateOutcome.Applied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))).value)
        }
        for ((message, parameter) in listOf(0x8 to 0L, WM_SHOWWINDOW to 0L, WM_SIZE to 1L)) {
            acquire(); assertTrue(hwnd in port.confinedWindows)
            port.dispatchWindowMessage(hwnd, message, parameter)
            assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
            assertTrue(port.confinedWindows.isEmpty())
            port.dispatchWindowMessage(hwnd, 0x7)
            assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
        }
        acquire()
        port.confinedWindows.remove(hwnd); port.confinementObservers.getValue(hwnd)()
        assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
        assertEquals(SurfaceFocus.Focused, window.surface.state.value.focus)
        port.confinementAvailable = false
        assertIs<SurfaceUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined)))).value)
        assertEquals(PointerCaptureMode.None, window.surface.state.value.pointerCapture)
    } }

    @Test fun encodedOrOversizedImagesAreRejectedBeforeNativeAllocation() = runBlocking { fixture { port, window, hwnd ->
        val png = BinaryImage(byteArrayOf(1, 2, 3), ImageFormat.Png, PhysicalSize(1, 1))
        assertIs<KadreResult.Failure>(window.apply(WindowUpdate(icon = PropertyChange.Set(png))))
        assertFalse(hwnd in port.icons)
        val large = BinaryImage(ByteArray(129 * 4), ImageFormat.Rgba8, PhysicalSize(129, 1))
        assertIs<SurfaceUpdateOutcome.PartiallyApplied>(assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(window.surface.apply(SurfaceUpdate(
            cursor = PropertyChange.Set(CursorStyle.Custom(CursorImage(large, PhysicalPoint(128, 0))))))).value)
        assertFalse(hwnd in port.cursors)
    } }

    @Test fun aCloseInsideTheNativeDragCannotPublishAStalePress() = runBlocking { fixture { port, window, hwnd ->
        val before = window.surface.input.state.value
        port.onNativeDrag = { runBlocking { window.close() } }
        window.surface.installInteractionHandler(InteractionHandler { context, _ -> context.request(InteractionAction.BeginWindowMove) })
        port.dispatchWindowMessage(hwnd, 0x201)
        assertEquals(WindowPhase.Closed, window.state.value.phase)
        assertEquals(before.keyboard, window.surface.input.state.value.keyboard)
        assertTrue(window.surface.input.state.value.pointers.all { it.pressedButtons.isEmpty() })
        assertTrue(port.confinedWindows.isEmpty()); assertTrue(port.icons.isEmpty()); assertTrue(port.cursors.isEmpty())
    } }

    @Test fun levelChangesDuringBorderlessSurviveWindowedRestoration() = runBlocking { fixture { _, window, _ ->
        window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
        window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating)))
        window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed)))
        assertEquals(WindowLevel.Floating, window.state.value.level)
        window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Borderless)))
        window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Normal)))
        window.apply(WindowUpdate(fullscreen = PropertyChange.Set(FullscreenMode.Windowed)))
        assertEquals(WindowLevel.Normal, window.state.value.level)
    } }
}
