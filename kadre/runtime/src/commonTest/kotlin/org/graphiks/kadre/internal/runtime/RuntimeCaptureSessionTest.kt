package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class RuntimeCaptureSessionTest {
    @Test
    fun sessionBudgetMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeCaptureManager(AdmissionCapturePort(enumeratedSnapshot(name = "Primary")), maxConcurrentSessions = 0)
        }
    }

    @Test
    fun staleSourceIsRejectedBeforeTheBackendReservation() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val stale = source(manager)
        port.refreshResult = KadreResult.Success(enumeratedSnapshot(name = "Renamed"))
        manager.refreshSources()

        val result = manager.open(CaptureRequest(CaptureTarget.Source(stale.id, stale.managerRevision)))

        assertEquals(
            KadreResult.Failure(KadreFailure.StaleRevision(expected = 1L, received = 0L)),
            result,
        )
        assertEquals(0, port.reserveCalls)
    }

    @Test
    fun reservationCountsAgainstTheSessionBudgetUntilTheSessionTerminates() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val firstReservation = RecordingCaptureReservation(portSource("Primary"))
        val secondReservation = RecordingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(firstReservation))
        port.reservations.addLast(KadreResult.Success(secondReservation))
        val request = CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))

        val first = successValue(manager.open(request))
        val rejected = manager.open(request)

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureSession, 1L)),
            rejected,
        )
        assertEquals(1, port.reserveCalls)

        first.close()
        val second = successValue(manager.open(request))

        assertEquals(2, port.reserveCalls)
        assertEquals(1, firstReservation.closeCount)
        assertEquals(CaptureSessionState.Terminated(CaptureOutcome.Stopped(org.graphiks.kadre.capture.CaptureStopReason.Requested)), first.state.value)
        second.close()
    }

    @Test
    fun sourceSessionKeepsItsPublishedDescriptorAfterTheInventoryIsWithdrawn() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        port.reservations.addLast(KadreResult.Success(RecordingCaptureReservation(portSource("Primary"))))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val failure = KadreFailure.PlatformFailure(org.graphiks.kadre.diagnostics.KadrePlatform.Fake, "capture", "lost")
        port.refreshResult = KadreResult.Failure(failure)

        manager.refreshSources()

        assertEquals(selected, session.source)
        assertIs<org.graphiks.kadre.capture.CaptureSources.Unavailable>(manager.state.value.sources)
        session.close()
    }
}

private class AdmissionCapturePort(
    override val initialSnapshot: CapturePortSnapshot,
) : CapturePort {
    lateinit var refreshResult: KadreResult<CapturePortSnapshot>
    val reservations = ArrayDeque<KadreResult<CapturePortReservation>>()
    var reserveCalls = 0
        private set

    override suspend fun requestPermission(
        scope: org.graphiks.kadre.capture.CapturePermissionScope,
    ): KadreResult<CapturePortSnapshot> = error("permission is not part of admission tests")

    override suspend fun refreshSources(): KadreResult<CapturePortSnapshot> = refreshResult

    override suspend fun reserve(
        target: CapturePortTarget,
        request: CaptureRequest,
    ): KadreResult<CapturePortReservation> {
        reserveCalls += 1
        return checkNotNull(reservations.removeFirstOrNull()) { "test did not enqueue a reservation" }
    }

    override fun installObserver(observer: (KadreResult<CapturePortSnapshot>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit
}

private class RecordingCaptureReservation(
    override val source: CapturePortSource,
) : CapturePortReservation {
    var closeCount = 0
        private set

    override fun close() {
        closeCount += 1
    }
}

private fun enumeratedSnapshot(name: String): CapturePortSnapshot = snapshot(
    permissions = CapturePermissionState(PermissionState.Granted, PermissionState.Granted),
    sources = CapturePortSources.Enumerated(listOf(portSource(name))),
)

private fun portSource(name: String): CapturePortSource = CapturePortSource(
    key = CapturePortSourceKey("display", 7L),
    kind = CaptureSourceKind.Display,
    name = name,
    size = PhysicalSize(1920, 1080),
)

private fun source(manager: RuntimeCaptureManager): CaptureSource =
    assertIs<org.graphiks.kadre.capture.CaptureSources.Enumerated>(manager.state.value.sources).values.single()

private fun <T> successValue(result: KadreResult<T>): T = when (result) {
    is KadreResult.Success -> result.value
    is KadreResult.Failure -> error("expected success, got ${result.reason}")
}
