package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.graphiks.kadre.capture.CaptureDiagnostic
import org.graphiks.kadre.capture.CaptureEvent
import org.graphiks.kadre.capture.CaptureFrame
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureSession
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureStopReason
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

/**
 * Reservation-owned capture session.
 *
 * A reservation is released exactly once and only then returns its manager's session slot. Frame
 * delivery is added on top of this lifecycle boundary without exposing a native owner publicly.
 */
internal class RuntimeCaptureSession(
    override val source: CaptureSource,
    private val reservation: CapturePortReservation,
    private val onTerminated: (RuntimeCaptureSession) -> Unit,
) : CaptureSession {
    private val lock = RuntimeLock()
    private val mutableState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Ready)
    private val termination = CompletableDeferred<CaptureOutcome>()
    private var stopping = false
    private var collectorClaimed = false
    private var stream: CapturePortStream? = null

    override val state: StateFlow<CaptureSessionState> = mutableState.asStateFlow()
    override val events: Flow<CaptureEvent> = emptyFlow()
    override val diagnostics: Flow<CaptureDiagnostic> = emptyFlow()

    override fun close() = stop(CaptureStopReason.Requested)

    override fun requestStop() = stop(CaptureStopReason.Requested)

    internal fun stopFromParent() = stop(CaptureStopReason.ParentSessionStopping)

    override suspend fun awaitTermination(): CaptureOutcome = termination.await()

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
            outcomeToResult(termination.await())
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
        override fun onTerminated(outcome: CaptureOutcome) {
            finish(outcome)
        }
    }

}
