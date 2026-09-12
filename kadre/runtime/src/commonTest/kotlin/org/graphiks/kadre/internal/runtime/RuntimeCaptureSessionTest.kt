package org.graphiks.kadre.internal.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.capture.CaptureManagerRevision
import org.graphiks.kadre.capture.CapturePermissionState
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSource
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.CaptureDiagnostic
import org.graphiks.kadre.capture.CaptureFrame
import org.graphiks.kadre.capture.CaptureConfiguration
import org.graphiks.kadre.capture.CaptureConfigurationRevision
import org.graphiks.kadre.capture.CaptureCadence
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.CaptureSessionState
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.PermissionState
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.policy.KadrePolicies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    @Test
    fun firstCollectorStartsTheReservedStreamAndCompletesFromItsTerminalCallback() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )

        val collecting = async { session.collectFrames { } }
        runCurrent()

        assertEquals(1, reservation.startCalls)
        assertEquals(CaptureSessionState.Streaming(reservation.configuration), session.state.value)

        reservation.complete(CaptureOutcome.SourceCompleted)

        assertEquals(KadreResult.Success(Unit), collecting.await())
        assertEquals(
            CaptureSessionState.Terminated(CaptureOutcome.SourceCompleted),
            session.state.value,
        )
    }

    @Test
    fun permissionRevocationKeepsItsTerminalOutcomeButFailsTheCollector() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val collecting = async { session.collectFrames { } }
        runCurrent()

        reservation.complete(CaptureOutcome.Stopped(org.graphiks.kadre.capture.CaptureStopReason.PermissionRevoked))

        assertEquals(
            KadreResult.Failure(KadreFailure.PermissionDenied(org.graphiks.kadre.input.KadrePermission.CaptureScreen)),
            collecting.await(),
        )
        assertEquals(
            CaptureSessionState.Terminated(
                CaptureOutcome.Stopped(org.graphiks.kadre.capture.CaptureStopReason.PermissionRevoked),
            ),
            session.state.value,
        )
    }

    @Test
    fun backendSourceLossIsMappedToThePublishedSourceIdentity() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val collecting = async { session.collectFrames { } }
        runCurrent()

        reservation.complete(CapturePortTermination.SourceLost)

        val expected = KadreFailure.SourceLost(selected.id)
        assertEquals(KadreResult.Failure(expected), collecting.await())
        assertEquals(CaptureSessionState.Terminated(CaptureOutcome.Failed(expected)), session.state.value)
    }

    @Test
    fun secondCollectorIsRejectedWithoutRestartingTheReservedStream() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val first = async { session.collectFrames { } }
        runCurrent()

        val second = session.collectFrames { }

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.CaptureCollector)),
            second,
        )
        assertEquals(1, reservation.startCalls)
        session.close()
        assertEquals(KadreResult.Success(Unit), first.await())
    }

    @Test
    fun collectorCancellationStopsTheSessionAndReleasesItsReservation() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val collecting = async { session.collectFrames { } }
        runCurrent()

        collecting.cancelAndJoin()

        assertTrue(collecting.isCancelled)
        assertEquals(
            CaptureSessionState.Terminated(CaptureOutcome.Stopped(org.graphiks.kadre.capture.CaptureStopReason.CollectorCancelled)),
            session.state.value,
        )
        assertEquals(1, reservation.closeCalls)
    }

    @Test
    fun collectorReceivesAFrameLeaseThatIsInvalidatedAfterItsCallback() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        var delivered: CaptureFrame? = null
        val collecting = async {
            session.collectFrames { frame ->
                delivered = frame
                assertEquals(byteArrayOf(1, 2, 3, 4).toList(), frame.copyPlanes().single().bytes.toList())
            }
        }
        runCurrent()

        reservation.emit(frame())
        runCurrent()
        reservation.complete(CaptureOutcome.SourceCompleted)

        assertEquals(KadreResult.Success(Unit), collecting.await())
        assertFailsWith<IllegalStateException> { checkNotNull(delivered).copyPlanes() }
    }

    @Test
    fun collectorCannotAwaitItsOwnSessionTermination() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        var awaitFailure: Throwable? = null
        val collecting = async {
            session.collectFrames {
                try {
                    session.awaitTermination()
                } catch (failure: Throwable) {
                    awaitFailure = failure
                }
            }
        }
        runCurrent()

        reservation.emit(frame())
        runCurrent()

        assertIs<IllegalStateException>(awaitFailure)
        reservation.complete(CaptureOutcome.SourceCompleted)
        assertEquals(KadreResult.Success(Unit), collecting.await())
    }

    @Test
    fun frameLargerThanTheCaptureByteBudgetTerminatesTheSession() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(
            port = port,
            maxConcurrentSessions = 1,
            capturePolicy = KadrePolicies.Default.capture.copy(maxBufferedBytesPerSession = 3L),
        )
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val collecting = async { session.collectFrames { } }
        runCurrent()

        assertEquals(3L, reservation.maximumFrameBytes)

        reservation.emit(frame())

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 3L)),
            collecting.await(),
        )
        assertEquals(
            CaptureSessionState.Terminated(
                CaptureOutcome.Failed(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 3L)),
            ),
            session.state.value,
        )
    }

    @Test
    fun latestDeliveryReplacesOnlyThePendingFrameAndReportsItsDrop() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        val releaseFirst = CompletableDeferred<Unit>()
        val delivered = mutableListOf<Byte>()
        val diagnostics = async { session.diagnostics.take(1).toList() }
        val collecting = async {
            session.collectFrames { frame ->
                delivered += frame.copyPlanes().single().bytes.first()
                if (delivered.size == 1) releaseFirst.await()
            }
        }
        runCurrent()

        reservation.emit(frame(1))
        runCurrent()
        reservation.emit(frame(2))
        reservation.emit(frame(3))

        val dropped = assertIs<CaptureDiagnostic.FrameDropped>(diagnostics.await().single())
        assertEquals(1L, dropped.count)
        releaseFirst.complete(Unit)
        reservation.complete(CaptureOutcome.SourceCompleted)

        assertEquals(KadreResult.Success(Unit), collecting.await())
        assertEquals(listOf(1, 3).map(Int::toByte), delivered)
    }

    @Test
    fun reconfigurationPublishesItsStateBeforeTheFirstFrameAtThatRevision() = runTest {
        val port = AdmissionCapturePort(enumeratedSnapshot(name = "Primary"))
        val manager = RuntimeCaptureManager(port, maxConcurrentSessions = 1)
        val selected = source(manager)
        val reservation = StreamingCaptureReservation(portSource("Primary"))
        port.reservations.addLast(KadreResult.Success(reservation))
        val session = successValue(
            manager.open(CaptureRequest(CaptureTarget.Source(selected.id, selected.managerRevision))),
        )
        var stateDuringFrame: CaptureSessionState? = null
        val collecting = async {
            session.collectFrames { frame ->
                stateDuringFrame = session.state.value
                assertEquals(CaptureConfigurationRevision(1), frame.configurationRevision)
            }
        }
        runCurrent()
        val reconfigured = reservation.configuration.copy(revision = CaptureConfigurationRevision(1))

        reservation.reconfigure(reconfigured)
        reservation.emit(frame(configurationRevision = 1L))
        runCurrent()
        reservation.complete(CaptureOutcome.SourceCompleted)

        assertEquals(KadreResult.Success(Unit), collecting.await())
        assertEquals(CaptureSessionState.Streaming(reconfigured), stateDuringFrame)
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

private class StreamingCaptureReservation(
    override val source: CapturePortSource,
) : CapturePortReservation {
    val configuration = CaptureConfiguration(
        revision = CaptureConfigurationRevision(0),
        size = PhysicalSize(1920, 1080),
        format = PixelFormat.Bgra8,
        colorEncoding = ColorEncoding(
            primaries = ColorPrimaries.Bt709,
            transfer = TransferFunction.Srgb,
            matrix = MatrixCoefficients.Identity,
            range = ColorRange.Full,
            hdr = HdrMetadata.None,
        ),
        alphaMode = AlphaMode.Premultiplied,
        orientation = CaptureOrientation.Upright,
        cadence = CaptureCadence.Unknown,
        region = null,
        cursorMode = CaptureCursorMode.EmbeddedWhenAvailable,
    )
    var startCalls = 0
        private set
    var closeCalls = 0
        private set
    var maximumFrameBytes: Long? = null
        private set
    private var listener: CapturePortStreamListener? = null

    override suspend fun start(
        listener: CapturePortStreamListener,
        maxFrameBytes: Long,
    ): KadreResult<CapturePortStreamStart> {
        startCalls += 1
        maximumFrameBytes = maxFrameBytes
        this.listener = listener
        return KadreResult.Success(
            CapturePortStreamStart(
                stream = object : CapturePortStream {
                    override fun close() = Unit
                },
                configuration = configuration,
            ),
        )
    }

    fun complete(outcome: CaptureOutcome) {
        complete(CapturePortTermination.Outcome(outcome))
    }

    fun complete(termination: CapturePortTermination) {
        checkNotNull(listener).onTerminated(termination)
    }

    fun emit(frame: CapturePortFrame) {
        checkNotNull(listener).onFrame(frame)
    }

    fun reconfigure(configuration: CaptureConfiguration) {
        checkNotNull(listener).onReconfigured(configuration)
    }

    override fun close() {
        closeCalls += 1
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

private fun frame(
    first: Byte = 1,
    configurationRevision: Long = 0L,
): CapturePortFrame = CapturePortFrame(
    size = PhysicalSize(1, 1),
    format = PixelFormat.Bgra8,
    planes = listOf(
        CapturePortPlane(
            layout = PixelPlaneLayout(1, 1, 4, 4, 4, 1, 1),
            bytes = byteArrayOf(first, 2, 3, 4),
        ),
    ),
    configurationRevision = configurationRevision,
    sourceTimestamp = null,
    duration = null,
    discontinuity = null,
    colorEncoding = ColorEncoding(
        primaries = ColorPrimaries.Bt709,
        transfer = TransferFunction.Srgb,
        matrix = MatrixCoefficients.Identity,
        range = ColorRange.Full,
        hdr = HdrMetadata.None,
    ),
    alphaMode = AlphaMode.Premultiplied,
    orientation = CaptureOrientation.Upright,
)

private fun source(manager: RuntimeCaptureManager): CaptureSource =
    assertIs<org.graphiks.kadre.capture.CaptureSources.Enumerated>(manager.state.value.sources).values.single()

private fun <T> successValue(result: KadreResult<T>): T = when (result) {
    is KadreResult.Success -> result.value
    is KadreResult.Failure -> error("expected success, got ${result.reason}")
}
