package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.graphiks.kadre.policy.CaptureDeliveryPolicy
import org.graphiks.kadre.policy.ContinuousOverflowAction
import org.graphiks.kadre.policy.FrameDelivery
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
    private val capturePolicy: CaptureDeliveryPolicy,
    private val eventStampSource: () -> EventStamp,
    private val onFatalFailure: (KadreFailure) -> Unit,
    private val onTerminated: (RuntimeCaptureSession) -> Unit,
) : CaptureSession {
    private val lock = RuntimeLock()
    private val mutableState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Ready)
    private val termination = CompletableDeferred<CaptureOutcome>()
    private var stopping = false
    private var collectorClaimed = false
    private var stream: CapturePortStream? = null
    private var queuedFrameBytes = 0L
    private var leasedFrameBytes = 0L
    private val frames = Channel<CapturePortFrame>(capacity = capturePolicy.frames.channelCapacity())
    private val mutableEvents = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = capturePolicy.events.ingressCapacity)
    private val mutableDiagnostics = MutableSharedFlow<CaptureDiagnostic>(
        extraBufferCapacity = capturePolicy.events.ingressCapacity,
    )

    override val state: StateFlow<CaptureSessionState> = mutableState.asStateFlow()
    override val events: Flow<CaptureEvent> = mutableEvents.asSharedFlow()
    override val diagnostics: Flow<CaptureDiagnostic> = mutableDiagnostics.asSharedFlow()

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
            reservation.start(
                listener = StreamListener(),
                maxFrameBytes = capturePolicy.maxBufferedBytesPerSession,
            )
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
        emitEvent(CaptureEvent.StreamingStarted(start.configuration, eventStampSource()))

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
        if (outcome != CaptureOutcome.SourceCompleted) discardQueuedFrames()
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
            val offer = lock.withLock { offerFrameLocked(frame) }
            when (offer) {
                FrameOffer.Accepted -> Unit
                is FrameOffer.Dropped -> emitDiagnostic(CaptureDiagnostic.FrameDropped(offer.count, eventStampSource()))
                is FrameOffer.Failed -> {
                    finish(CaptureOutcome.Failed(offer.failure))
                    if (offer.failSession) onFatalFailure(offer.failure)
                }
            }
        }

        override fun onTerminated(outcome: CaptureOutcome) {
            finish(outcome)
        }

        override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) {
            val accepted = lock.withLock {
                if (stopping) {
                    false
                } else {
                    val current = mutableState.value as? CaptureSessionState.Streaming
                        ?: error("capture stream reconfigured before startup")
                    check(configuration.revision.value == current.configuration.revision.value + 1L) {
                        "capture configuration revisions must advance one step at a time"
                    }
                    mutableState.value = CaptureSessionState.Streaming(configuration)
                    true
                }
            }
            if (accepted) emitEvent(CaptureEvent.Reconfigured(configuration, eventStampSource()))
        }
    }

    private suspend fun collectDeliveredFrames(collector: suspend (CaptureFrame) -> Unit): KadreResult<Unit> {
        while (true) {
            val portFrame = frames.receiveCatching().getOrNull() ?: return outcomeToResult(termination.await())
            val bytes = portFrame.byteCount()
            lock.withLock {
                check(queuedFrameBytes >= bytes) { "capture queued-byte accounting underflow" }
                queuedFrameBytes -= bytes
                leasedFrameBytes += bytes
            }
            val frame = RuntimeCaptureFrame(portFrame, eventStampSource()) { releaseLeasedBytes(bytes) }
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

    private fun offerFrameLocked(frame: CapturePortFrame): FrameOffer {
        if (stopping) return FrameOffer.Dropped(1)
        val bytes = frame.byteCount()
        if (bytes > capturePolicy.maxBufferedBytesPerSession) {
            return FrameOffer.Failed(
                KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, capturePolicy.maxBufferedBytesPerSession),
                failSession = false,
            )
        }
        var dropped = 0L
        while (
            queuedFrameBytes + leasedFrameBytes + bytes > capturePolicy.maxBufferedBytesPerSession ||
            frames.trySend(frame).isFailure
        ) {
            when (val overflow = capturePolicy.frames.overflowAction()) {
                FrameOverflowAction.DropOldest -> {
                    val removed = frames.tryReceive().getOrNull()
                    if (removed == null) return FrameOffer.Dropped(dropped + 1L)
                    check(queuedFrameBytes >= removed.byteCount()) { "capture queued-byte accounting underflow" }
                    queuedFrameBytes -= removed.byteCount()
                    dropped += 1L
                }

                FrameOverflowAction.DropLatest -> return FrameOffer.Dropped(dropped + 1L)
                FrameOverflowAction.CloseSource -> return FrameOffer.Failed(
                    KadreFailure.SourceOverflow(KadreResourceKind.CaptureBuffer),
                    failSession = false,
                )

                FrameOverflowAction.FailSession -> return FrameOffer.Failed(
                    KadreFailure.SourceOverflow(KadreResourceKind.CaptureBuffer),
                    failSession = true,
                )
            }
        }
        queuedFrameBytes += bytes
        return if (dropped == 0L) FrameOffer.Accepted else FrameOffer.Dropped(dropped)
    }

    private fun discardQueuedFrames() {
        lock.withLock {
            while (true) {
                val frame = frames.tryReceive().getOrNull() ?: break
                check(queuedFrameBytes >= frame.byteCount()) { "capture queued-byte accounting underflow" }
                queuedFrameBytes -= frame.byteCount()
            }
        }
    }

    private fun releaseLeasedBytes(bytes: Long) {
        lock.withLock {
            check(leasedFrameBytes >= bytes) { "capture leased-byte accounting underflow" }
            leasedFrameBytes -= bytes
        }
    }

    private fun emitEvent(event: CaptureEvent) {
        mutableEvents.tryEmit(event)
    }

    private fun emitDiagnostic(diagnostic: CaptureDiagnostic) {
        mutableDiagnostics.tryEmit(diagnostic)
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
    private val onClose: () -> Unit,
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
            onClose()
        }
    }

    override fun copyPlanes(): List<CopiedPixelPlane> = lock.withLock {
        check(active) { "capture frame lease is closed" }
        checkNotNull(copiedPlanes).map { plane ->
            CopiedPixelPlane(plane.layout, plane.bytes.copyOf())
        }
    }
}

private sealed interface FrameOffer {
    public data object Accepted : FrameOffer
    public data class Dropped(val count: Long) : FrameOffer
    public data class Failed(val failure: KadreFailure, val failSession: Boolean) : FrameOffer
}

private enum class FrameOverflowAction { DropOldest, DropLatest, CloseSource, FailSession }

private fun FrameDelivery.channelCapacity(): Int = when (this) {
    FrameDelivery.Latest -> 1
    is FrameDelivery.Buffered -> capacity
}

private fun FrameDelivery.overflowAction(): FrameOverflowAction = when (this) {
    FrameDelivery.Latest -> FrameOverflowAction.DropOldest
    is FrameDelivery.Buffered -> when (onOverflow) {
        ContinuousOverflowAction.DropOldestAndReport -> FrameOverflowAction.DropOldest
        ContinuousOverflowAction.DropLatestAndReport -> FrameOverflowAction.DropLatest
        ContinuousOverflowAction.CloseSource -> FrameOverflowAction.CloseSource
        ContinuousOverflowAction.FailSession -> FrameOverflowAction.FailSession
    }
}

private fun CapturePortFrame.byteCount(): Long {
    var total = 0L
    planes.forEach { plane ->
        val bytes = plane.layout.byteCount.toLong()
        check(Long.MAX_VALUE - total >= bytes) { "capture frame byte count overflow" }
        total += bytes
    }
    return total
}
