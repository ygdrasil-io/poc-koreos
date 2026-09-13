package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.graphiks.kadre.capture.CaptureDiagnostic
import org.graphiks.kadre.capture.CaptureEvent
import org.graphiks.kadre.capture.CaptureFrame
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.capture.CaptureSourceInstant
import org.graphiks.kadre.capture.CopiedPixelPlane
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Reservation-owned capture session.
 *
 * A reservation is released exactly once and only then returns its manager's session slot. Frame
 * delivery is added on top of this lifecycle boundary without exposing a native owner publicly.
 */
internal class RuntimeCaptureSession(
    override val source: CaptureSource,
    private val reservation: CapturePortReservation,
    private val eventStampSource: () -> EventStamp,
    private val onTerminated: (RuntimeCaptureSession) -> Unit,
) : CaptureSession {
    private val lock = RuntimeLock()
    private val mutableState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Ready)
    private val termination = CompletableDeferred<CaptureOutcome>()
    private var stopping = false
    private var collectorClaimed = false
    private var stream: CapturePortStream? = null
    private val frames = Channel<CapturePortFrame>(capacity = 1)

    override val state: StateFlow<CaptureSessionState> = mutableState.asStateFlow()
    override val events: Flow<CaptureEvent> = emptyFlow()
    override val diagnostics: Flow<CaptureDiagnostic> = emptyFlow()

    override fun close() = stop(CaptureStopReason.Requested)

    override fun requestStop() = stop(CaptureStopReason.Requested)

    internal fun stopFromParent() = stop(CaptureStopReason.ParentSessionStopping)

    override suspend fun awaitTermination(): CaptureOutcome {
        check(currentCoroutineContext()[CaptureCollectorMarker]?.session !== this) {
            "a capture collector cannot await its own session termination"
        }
        return termination.await()
    }

    override suspend fun collectFrames(collector: suspend (CaptureFrame) -> Unit): KadreResult<Unit> {
        val admission = lock.withLock {
            when {
                stopping -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
                collectorClaimed -> KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector))
                else -> {
                    collectorClaimed = true
                    null
                }
            }
        }
        if (admission != null) return admission

        val started = try {
            reservation.start(StreamListener())
        } catch (cause: CancellationException) {
            stop(CaptureStopReason.CollectorCancelled)
            throw cause
        }
        val start = when (started) {
            is KadreResult.Success -> started.value
            is KadreResult.Failure -> {
                finish(CaptureOutcome.Failed(started.reason))
                return started
            }
        }
        val accepted = lock.withLock {
            if (stopping) {
                false
            } else {
                stream = start.stream
                mutableState.value = CaptureSessionState.Streaming(start.configuration)
                true
            }
        }
        if (!accepted) {
            start.stream.close()
            return KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
        }

        return try {
            withContext(CaptureCollectorMarker(this)) {
                collectDeliveredFrames(collector)
            }
        } catch (cause: CancellationException) {
            stop(CaptureStopReason.CollectorCancelled)
            throw cause
        }
    }

    private fun stop(reason: CaptureStopReason) {
        finish(CaptureOutcome.Stopped(reason))
    }

    private fun finish(outcome: CaptureOutcome) {
        val stream = lock.withLock {
            if (stopping) {
                return
            } else {
                stopping = true
                mutableState.value = CaptureSessionState.Stopping
                this.stream.also { this.stream = null }
            }
        }
        runCatching { stream?.close() }
        runCatching { reservation.close() }
        frames.close()
        lock.withLock {
            mutableState.value = CaptureSessionState.Terminated(outcome)
        }
        termination.complete(outcome)
        onTerminated(this)
    }

    private fun outcomeToResult(outcome: CaptureOutcome): KadreResult<Unit> = when (outcome) {
        CaptureOutcome.SourceCompleted,
        is CaptureOutcome.Stopped,
        -> KadreResult.Success(Unit)

        is CaptureOutcome.Failed -> KadreResult.Failure(outcome.failure)
    }

    private inner class StreamListener : CapturePortStreamListener {
        override fun onFrame(frame: CapturePortFrame) {
            lock.withLock {
                if (stopping) {
                    return@withLock
                } else if (frames.trySend(frame).isSuccess) {
                    return@withLock
                } else {
                    val replaced = frames.tryReceive().getOrNull()
                    if (replaced != null) frames.trySend(frame)
                }
            }
        }

        override fun onTerminated(outcome: CaptureOutcome) {
            finish(outcome)
        }
    }

    private suspend fun collectDeliveredFrames(collector: suspend (CaptureFrame) -> Unit): KadreResult<Unit> {
        while (true) {
            val portFrame = frames.receiveCatching().getOrNull() ?: return outcomeToResult(termination.await())
            val frame = RuntimeCaptureFrame(portFrame, eventStampSource())
            try {
                collector(frame)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                stop(CaptureStopReason.CollectorFailed)
                throw cause
            } finally {
                frame.close()
            }
        }
    }

}

private class CaptureCollectorMarker(
    val session: RuntimeCaptureSession,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CaptureCollectorMarker>
}

/** Callback-scoped public view over a detached runtime-owned frame buffer. */
private class RuntimeCaptureFrame(
    private val portFrame: CapturePortFrame,
    override val stamp: EventStamp,
) : CaptureFrame {
    private val lock = RuntimeLock()
    private var active = true
    private var copiedPlanes: List<CapturePortPlane>? = portFrame.planes

    override val size: org.graphiks.kadre.surface.PhysicalSize = portFrame.size
    override val format: org.graphiks.kadre.capture.PixelFormat = portFrame.format
    override val planes: List<org.graphiks.kadre.capture.PixelPlaneLayout> = portFrame.planes.map(CapturePortPlane::layout)
    override val configurationRevision = org.graphiks.kadre.capture.CaptureConfigurationRevision(portFrame.configurationRevision)
    override val sourceTimestamp: CaptureSourceInstant? = portFrame.sourceTimestamp?.let(::CaptureSourceInstant)
    override val duration = portFrame.duration
    override val discontinuity = portFrame.discontinuity
    override val colorEncoding = portFrame.colorEncoding
    override val alphaMode = portFrame.alphaMode
    override val orientation = portFrame.orientation

    override fun close() {
        lock.withLock {
            if (!active) return
            active = false
            copiedPlanes = null
        }
    }

    override fun copyPlanes(): List<CopiedPixelPlane> = lock.withLock {
        check(active) { "capture frame lease is closed" }
        checkNotNull(copiedPlanes).map { plane ->
            CopiedPixelPlane(plane.layout, plane.bytes.copyOf())
        }
    }
}
