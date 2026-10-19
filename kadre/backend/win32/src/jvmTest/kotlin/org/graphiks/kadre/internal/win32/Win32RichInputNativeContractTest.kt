@file:OptIn(org.graphiks.kadre.diagnostics.DelicateKadreApi::class, org.graphiks.kadre.diagnostics.KadrePlatformApi::class)
package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.interaction.*
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import org.graphiks.kffi.win32.managed.Win32WindowOperations
import org.graphiks.kffi.win32.testsupport.Win32InjectedTouchHarness
import org.graphiks.kffi.win32.testsupport.Win32InjectedTouchHarness.Contact
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.*

/** Actual injected Windows frames and actual Ninput callbacks, never physical hardware receipts. */
class Win32RichInputNativeContractTest {
    @Test fun realInjectedContactsReachPublicIndependentIds() = runBlocking {
        fixture { port, window, hwnd ->
            val events = mutableListOf<InputEvent>(); val interactions = mutableListOf<InteractionEvent.TouchStarted>()
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val registration = assertIs<KadreResult.Success<InteractionRegistration>>(window.surface.installInteractionHandler(
                InteractionHandler { _, event -> if (event is InteractionEvent.TouchStarted) interactions += event })).value
            val collector = scope.launch(start=CoroutineStart.UNDISPATCHED) { window.surface.input.events.collect { events += it } }
            try {
                Win32InjectedTouchHarness.drive(hwnd) {
                    frame(Contact(0, 100, 100, DOWN))
                    frame(Contact(0, 100, 100, MOVE), Contact(1, 200, 100, DOWN))
                    frame(Contact(0, 120, 120, MOVE), Contact(1, 220, 120, MOVE))
                    frame(Contact(0, 120, 120, UP), Contact(1, 220, 120, MOVE))
                    frame(Contact(1, 220, 120, UP))
                }
                val touch = events.filterIsInstance<InputEvent.TouchChanged>()
                val starts = touch.filter { it.phase == TouchPhase.Started }
                assertEquals(2, starts.size, "exactly one public start per native contact")
                assertEquals(2, starts.map { it.touchId }.toSet().size)
                assertEquals(starts.map { it.touchId }, interactions.map { it.touchId })
                assertEquals(starts.map { it.stamp }, interactions.map { it.stamp })
                assertTrue(starts.all { it.pressure == 0.5 })
                for (start in starts) {
                    val lifetime = touch.filter { it.touchId == start.touchId }
                    assertTrue(lifetime.any { it.phase == TouchPhase.Moved })
                    assertEquals(TouchPhase.Ended, lifetime.last().phase)
                }
                assertTrue(events.none { it is InputEvent.PointerButtonChanged }, "promoted mouse must not duplicate touch")
                assertTrue(window.surface.input.state.value.touches.isEmpty())
                assertEquals(FeatureAvailability.Available, window.surface.input.state.value.capabilities.touch)
            } finally { collector.cancel(); registration.close() }
        }
    }
    @Test fun realInteractionContextPublishesPublicGestures() = runBlocking {
        fixture { _, window, hwnd ->
            val events = mutableListOf<InputEvent.Gesture>()
            val collector = CoroutineScope(Dispatchers.Unconfined).launch(start=CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.collect { if (it is InputEvent.Gesture) events += it }
            }
            try {
                val advertised = assertIs<Capability.Supported<Set<GestureKind>>>(window.surface.input.state.value.capabilities.gestures)
                assertEquals(setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap), advertised.constraints)
                Win32InjectedTouchHarness.drive(hwnd) {
                    frame(Contact(0, 100, 200, DOWN))
                    frame(Contact(0, 100, 200, MOVE), Contact(1, 200, 200, DOWN))
                    repeat(8) { index -> frame(Contact(0, 105 + index * 5, 205 + index * 5, MOVE), Contact(1, 205 + index * 5, 205 + index * 5, MOVE)) }
                    repeat(8) { index -> frame(Contact(0, 140, 240, MOVE), Contact(1, 245 + index * 5, 240 + index * 5, MOVE)) }
                    frame(Contact(0, 140, 240, UP), Contact(1, 280, 275, UP))
                }
                Win32InjectedTouchHarness.drive(hwnd) {
                    frame(Contact(0, 300, 300, DOWN)); frame(Contact(0, 300, 300, UP))
                    frame(Contact(0, 300, 300, DOWN)); frame(Contact(0, 300, 300, UP))
                }
                assertTrue(events.any { it.kind == GestureKind.Pan && abs(it.delta?.x ?: 0.0) > 0.01 })
                assertTrue(events.any { it.kind == GestureKind.Pinch && abs((it.scale ?: 1.0) - 1) > 0.001 })
                assertTrue(events.any { it.kind == GestureKind.Rotation && abs(it.rotationRadians ?: 0.0) > 0.001 })
                assertTrue(events.any { it.kind == GestureKind.DoubleTap && it.phase == TouchPhase.Ended })
                for (kind in setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation)) {
                    val lifetime = events.filter { it.kind == kind }
                    assertEquals(TouchPhase.Started, lifetime.first().phase)
                    assertEquals(TouchPhase.Ended, lifetime.last().phase)
                }
                assertTrue(events.none { it.kind == GestureKind.TouchpadPressure })
            } finally { collector.cancel() }
        }
    }
    @Test fun workerCloseStopsInputBeforeWindowDestruction() = runBlocking {
        var destroyed = false; var observationAtDestroy = -1
        var windowForReadback: Window? = null
        val port = object : KffiWin32HostPort() {
            override fun destroyWindow(hwnd: Long) {
                observationAtDestroy = windowForReadback?.surface?.input?.state?.value?.touches?.size ?: -1
                super.destroyWindow(hwnd); destroyed = true
            }
        }
        fixture(port) { native, window, hwnd ->
            windowForReadback=window
            Win32InjectedTouchHarness.drive(hwnd) { frame(Contact(0, 100, 100, DOWN)) }
            // The harness closes its injection sequence with a real UP before returning.
            val worker = Thread { runBlocking { window.close() } }.apply { start() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while ((!destroyed || worker.isAlive) && System.nanoTime() < deadline) { native.drainOneMessage(10); yield() }
            assertFalse(worker.isAlive); assertTrue(destroyed); assertEquals(0, observationAtDestroy)
            assertEquals(SurfaceAttachmentState.Detached, window.surface.state.value.attachment)
        }
    }
    private suspend fun fixture(port: KffiWin32HostPort = KffiWin32HostPort(), block: suspend (KffiWin32HostPort, Window, Long) -> Unit) {
        val host = Win32HostSession.start(port); val failures = mutableListOf<Throwable>()
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources,
            failureReporter=RuntimeFailureReporter { failures += it })
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(
                WindowSpec(title="Kadre native rich input proof", contentSize=LogicalSize(700.0, 600.0)))).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val hwnd = assertIs<KadreResult.Success<Long>>(window.withDesktopHandle { (it as DesktopNativeWindowHandle.Win32).hwnd.toLong() }).value
            val before = port.readWindow(hwnd).outerBounds
            port.setWindowBounds(hwnd, PhysicalRect(PhysicalPoint(10, 10), before.size), false)
            Win32WindowOperations.setTopmost(hwnd, true)
            Win32InjectedTouchHarness.prepareForeground(hwnd)
            assertTrue(failures.isEmpty(), "setup failures=${failures.size}")
            block(port, window, hwnd)
            assertTrue(failures.isEmpty(), "native input failures=${failures.size}")
        } finally { driver.close(); host.close(); port.close() }
    }
    companion object {
        private const val DOWN=0x10006; private const val MOVE=0x20006; private const val UP=0x40000
    }
}
