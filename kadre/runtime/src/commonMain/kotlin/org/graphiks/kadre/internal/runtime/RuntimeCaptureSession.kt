package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.CompletableDeferred
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
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult

/**
 * Reservation-owned capture session.
 *
 * Frame delivery is intentionally delegated to the following streaming slice. This session still
 * provides the lifecycle boundary required for admission: a reservation is released exactly once
 * and only then returns its manager's session slot.
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

    override val state: StateFlow<CaptureSessionState> = mutableState.asStateFlow()
    override val events: Flow<CaptureEvent> = emptyFlow()
    override val diagnostics: Flow<CaptureDiagnostic> = emptyFlow()

    override fun close() = stop(CaptureStopReason.Requested)

    override fun requestStop() = stop(CaptureStopReason.Requested)

    internal fun stopFromParent() = stop(CaptureStopReason.ParentSessionStopping)

    override suspend fun awaitTermination(): CaptureOutcome = termination.await()

    override suspend fun collectFrames(collector: suspend (CaptureFrame) -> Unit): KadreResult<Unit> =
        when (state.value) {
            is CaptureSessionState.Terminated -> KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.CaptureSession))
            else -> KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureCollectFrames))
        }

    private fun stop(reason: CaptureStopReason) {
        val shouldStop = lock.withLock {
            if (stopping) {
                false
            } else {
                stopping = true
                mutableState.value = CaptureSessionState.Stopping
                true
            }
        }
        if (!shouldStop) return

        runCatching { reservation.close() }
        val outcome = CaptureOutcome.Stopped(reason)
        lock.withLock {
            mutableState.value = CaptureSessionState.Terminated(outcome)
        }
        termination.complete(outcome)
        onTerminated(this)
    }
}
