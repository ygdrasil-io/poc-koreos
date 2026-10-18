package org.graphiks.kadre.internal.win32

import org.graphiks.kadre.input.*
import org.graphiks.kadre.internal.runtime.*
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.window.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class Win32ContactTest {
    @Test fun penHoverLeaveRetiresItsIdentityBeforeNativeIdReuse() {
        val f = Fixture()
        f.sendMessage(0x249, 1, sample(9, 2).copy(type=3))
        val first = f.events.filterIsInstance<SurfaceStimulus.PointerEntered>().single()
        f.sendMessage(0x24a, 1, sample(9, 2).copy(type=3, target=0))
        assertSame(first.nativeIdentity, f.events.filterIsInstance<SurfaceStimulus.PointerLeft>().single().nativeIdentity)
        f.sendMessage(0x249, 2, sample(9, 2).copy(type=3, eraser=true))
        val next = f.events.filterIsInstance<SurfaceStimulus.PointerEntered>().last()
        assertEquals(PointerKind.Eraser, next.kind)
        assertNotSame(first.nativeIdentity, next.nativeIdentity)
        assertEquals(0, f.retained); f.router.close()
    }

    @Test fun recognizerProcessFailurePreservesRawContacts() {
        val f = Fixture(); val failures=mutableListOf<Throwable>(); f.onFailure=failures::add
        f.gestures.processFailure=IllegalStateException("recognizer")
        f.send(1, sample(4, DOWN)); f.send(2, sample(4, MOVE)); f.send(3, sample(4, UP))
        assertTrue(f.router.installed)
        assertEquals(1, f.interactions.size)
        assertEquals(listOf(TouchPhase.Moved, TouchPhase.Ended), f.events.filterIsInstance<SurfaceStimulus.TouchChanged>().map { it.phase })
        assertEquals(1, failures.size); assertEquals(1, f.gestures.closes)
        assertEquals(0, f.retained); f.router.close()
    }

    @Test fun recognizerResetFailurePreservesNewRawContacts() {
        val f = Fixture(); val failures=mutableListOf<Throwable>(); f.onFailure=failures::add
        f.send(1, sample(4, DOWN)); f.gestures.resetFailure=IllegalStateException("reset")
        f.router.reset(); f.send(2, sample(5, DOWN)); f.send(3, sample(5, UP))
        assertTrue(f.router.installed); assertEquals(2, f.interactions.size)
        assertEquals(TouchPhase.Ended, f.events.filterIsInstance<SurfaceStimulus.TouchChanged>().last().phase)
        assertEquals(1, failures.size); assertEquals(1, f.gestures.closes); f.router.close()
    }

    @Test fun recognizerInitializationFailureOnlyWithdrawsGestures() = runBlocking {
        val port = object : DeterministicWin32HostPort() {
            override val pointerPacketsAvailable = true
            override fun createGesturePort(hwnd: Long, maxPointers: Int, onOutput: (Win32GestureOutput) -> Unit): Win32GesturePort = error("recognizer")
        }
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources, failureReporter=RuntimeFailureReporter {})
        try {
            val request=assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec())).value
            val window=assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            val capabilities=window.surface.input.state.value.capabilities
            assertEquals(FeatureAvailability.Available, capabilities.touch)
            assertIs<FeatureAvailability.Unavailable>(assertIs<Capability.Supported<Set<GestureKind>>>(capabilities.gestures).availability)
            Unit
        } finally { driver.close(); port.close() }
    }
    @Test fun twoContactsKeepIndependentLifetimes() {
        val f = Fixture()
        f.send(1, sample(4, DOWN), sample(5, DOWN))
        val starts = f.interactions.map { assertIs<RuntimeSynchronousInteraction.TouchContactStarted>(it) }
        assertEquals(2, starts.size); assertNotSame(starts[0].nativeIdentity, starts[1].nativeIdentity)
        f.send(2, sample(4, UP), sample(5, MOVE))
        f.send(3, sample(4, DOWN), sample(5, MOVE))
        val replacement = assertIs<RuntimeSynchronousInteraction.TouchContactStarted>(f.interactions.last())
        assertNotSame(starts[0].nativeIdentity, replacement.nativeIdentity)
        assertSame(starts[1].nativeIdentity, f.events.filterIsInstance<SurfaceStimulus.TouchChanged>().last().nativeIdentity)
        assertEquals(3, f.interactions.size); f.router.close(); assertEquals(0, f.retained)
    }
    @Test fun penAndMouseRemainDistinctThroughTheSynchronousHandler() {
        val f = Fixture()
        f.send(1, sample(9, DOWN).copy(type=3, pressure=0.5, tiltX=15.0, eraser=true))
        val pen = assertIs<RuntimeSynchronousInteraction.PointerPressed>(f.interactions.single())
        assertEquals(PointerKind.Eraser, pen.kind); assertEquals(0.5, pen.pressure)
        assertEquals(15.0, pen.pen?.tiltXDegrees); assertNotNull(pen.nativeIdentity)
        assertIs<SurfaceStimulus.PointerEntered>(f.events.first())
        f.send(2, sample(9, UP).copy(type=3, eraser=true))
        assertEquals(PointerButtonState.Released, f.events.filterIsInstance<SurfaceStimulus.PointerButtonChanged>().last().buttonState)
        f.router.close()
    }
    @Test fun captureLossCancelsWithoutWaitingForUp() {
        val f = Fixture(); f.send(1, sample(4, DOWN))
        assertEquals(0L, f.router.handle(0x24c, 4, 0))
        assertEquals(TouchPhase.Cancelled, f.events.filterIsInstance<SurfaceStimulus.TouchChanged>().single().phase)
        f.send(2, sample(4, MOVE)); assertEquals(1, f.interactions.size)
        f.send(3, sample(4, DOWN)); assertEquals(2, f.interactions.size)
        f.router.close()
    }
    @Test fun promotedMouseDoesNotDuplicateContactActivation() {
        val f = Fixture()
        val input = Win32InputRouter(1, SurfaceId(1), f.port, { f.events += it; true }, f.interactions::add, { true }, contacts=f.router)
        input.activate(); f.promoted = true
        assertEquals(0L, input.handle(0x201, 1, 0))
        assertTrue(f.interactions.isEmpty())
        f.promoted=false; input.handle(0x201, 1, 0)
        assertEquals(1, f.interactions.size); input.close()
    }
    @Test fun nestedNativeMessagesAreCopiedAndDrainedAfterTheHandler() {
        val f = Fixture(); var nested = false
        f.onInteraction = {
            if (!nested) {
                nested=true; f.send(2, sample(5, DOWN))
                assertEquals(1, f.interactions.size)
                assertEquals(2, f.retained, "both owned packets stay retained until the handler returns")
            }
        }
        f.send(1, sample(4, DOWN))
        assertEquals(2, f.interactions.size); assertEquals(0, f.retained); f.router.close()
    }
    @Test fun resetDuringTheHandlerDiscardsOldQueuedPacketsAndDefersRecognizerReset() {
        val f = Fixture(); f.onInteraction = {
            f.send(2, sample(5, DOWN)); f.router.reset()
            assertEquals(0, f.gestures.resets)
        }
        f.send(1, sample(4, DOWN))
        assertEquals(1, f.interactions.size); assertEquals(1, f.gestures.resets)
        assertEquals(0, f.retained); f.router.close()
    }
    @Test fun repeatedHistoryDoesNotReplayAnEndedContact() {
        val f = Fixture(); f.send(1, sample(4, DOWN)); f.send(2, sample(4, UP)); f.send(1, sample(4, DOWN))
        assertEquals(1, f.interactions.size); f.router.close()
    }
    @Test fun anActiveContactsRepeatedDownFlagDoesNotStartItTwice() {
        val f = Fixture(); f.send(1, sample(4, DOWN)); f.send(2, sample(4, DOWN), sample(5, DOWN))
        assertEquals(2, f.interactions.size); f.router.close()
    }
    @Test fun penHoverHasItsActualDeltaAndBarrelButton() {
        val f = Fixture(); f.send(1, sample(9, 2).copy(type=3))
        f.send(2, sample(9, 0x20002).copy(type=3, x=160, y=180))
        assertEquals(LogicalDelta(20.0, 20.0), f.events.filterIsInstance<SurfaceStimulus.PointerMoved>().last().delta)
        f.send(3, sample(9, 0x20022).copy(type=3, x=160, y=180))
        assertEquals(PointerButton.Barrel, assertIs<RuntimeSynchronousInteraction.PointerPressed>(f.interactions.single()).button)
        f.router.close()
    }
    @Test fun closingRevokesWithoutReconfiguringTheDyingNativeContext() {
        val f = Fixture(); f.send(1, sample(4, DOWN)); f.router.close(); f.router.close()
        assertEquals(0, f.gestures.resets); assertEquals(1, f.gestures.closes)
        assertEquals(TouchPhase.Cancelled, f.events.filterIsInstance<SurfaceStimulus.TouchChanged>().last().phase)
    }
    @Test fun retainedByteFailureKeepsItsTypedReasonOnThePublicSource() = runBlocking {
        val failure = KadreFailure.ResourceLimitExceeded(KadreResourceKind.RetainedPayload, 16)
        val port = object : DeterministicWin32HostPort() {
            override val pointerPacketsAvailable: Boolean = true
            override fun createGesturePort(hwnd: Long, maxPointers: Int, onOutput: (Win32GestureOutput) -> Unit): Win32GesturePort = Gestures()
            override fun readPointerBatch(pointerId: Int, maxPointers: Int, maxFrames: Int, budget: RuntimePayloadBudget): Win32ContactBatch = throw KadreException(failure)
        }
        val driver = Win32WindowRuntimeDriverFactory.create(port, KadrePolicies.Default.resources, failureReporter=RuntimeFailureReporter {})
        try {
            val request = assertIs<KadreResult.Success<WindowRequest>>(driver.manager.requestWindow(WindowSpec())).value
            val window = assertIs<WindowRequestOutcome.OpenedHere>(request.await()).window
            port.dispatchWindowMessage(port.lastTopLevelHwnd(), 0x245, 1, 0)
            assertEquals(FeatureAvailability.Unavailable(failure), window.surface.input.state.value.capabilities.touch)
        } finally { driver.close(); port.close() }
    }
    @Test fun nestedPacketOverflowReleasesEveryOwnedBatch() {
        val f = Fixture(); val failures=mutableListOf<Throwable>(); f.onFailure = failures::add
        f.onInteraction = { repeat(5) { f.send(2 + it, sample(5 + it, DOWN)) } }
        f.send(1, sample(4, DOWN))
        assertEquals(1, failures.size); assertIs<KadreFailure.SourceOverflow>(assertIs<KadreException>(failures.single()).failure)
        assertEquals(0, f.retained); assertEquals(1, f.gestures.closes)
    }

    internal class Fixture {
        val events = mutableListOf<SurfaceStimulus>(); val interactions = mutableListOf<RuntimeSynchronousInteraction>()
        val gestures = Gestures(); var retained=0; var onInteraction: (() -> Unit)? = null
        var promoted=false
        var onFailure: (Throwable) -> Unit = { throw AssertionError(it) }
        val batches = ArrayDeque<Win32ContactBatch>()
        val port = object : DeterministicWin32HostPort() {
            override val pointerPacketsAvailable: Boolean = true
            override fun isPromotedMouseMessage(): Boolean = promoted
            override fun readPointerBatch(pointerId: Int, maxPointers: Int, maxFrames: Int, budget: RuntimePayloadBudget): Win32ContactBatch = batches.removeFirst()
            override fun pointerClientPosition(hwnd: Long, x: Int, y: Int): LogicalPoint = LogicalPoint(x / 2.0, y / 2.0)
            override fun createGesturePort(hwnd: Long, maxPointers: Int, onOutput: (Win32GestureOutput) -> Unit): Win32GesturePort = gestures
        }
        val router = Win32ContactRouter(1, SurfaceId(1), port, KadrePolicies.Default.resources,
            RuntimePayloadBudget { org.graphiks.kadre.diagnostics.KadreResult.Success(AutoCloseable {}) }, 4,
            { events += it; true }, { interactions += it; onInteraction?.invoke() }, { true }, { onFailure(it) })
        init { router.activate(); events.clear() }
        fun send(frame: Int, vararg samples: Win32ContactSample) {
            sendMessage(0x245, frame, *samples)
        }
        fun sendMessage(message: Int, frame: Int, vararg samples: Win32ContactSample) {
            retained++; batches += Win32ContactBatch(listOf(Win32ContactFrame(frame, samples.toList())), AutoCloseable { retained-- })
            assertEquals(0L, router.handle(message, samples.first().id.toLong(), 0))
        }
    }
    internal class Gestures : Win32GesturePort {
        override val kinds = setOf(GestureKind.Pan, GestureKind.Pinch, GestureKind.Rotation, GestureKind.DoubleTap)
        var resets=0; var closes=0
        var processFailure: Throwable? = null; var resetFailure: Throwable? = null
        override fun process(batch: Win32ContactBatch) { processFailure?.let { throw it } }
        override fun reset() { resets++; resetFailure?.let { throw it } }
        override fun close() { closes++ }
    }
    companion object {
        const val DOWN=0x10006; const val MOVE=0x20006; const val UP=0x40000
        internal fun sample(id: Int, flags: Int) = Win32ContactSample(id, 2, flags, 120, 140, target=1)
    }
}
