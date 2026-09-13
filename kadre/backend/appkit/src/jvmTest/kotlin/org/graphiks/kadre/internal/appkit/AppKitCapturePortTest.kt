package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureCursorMode
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.capture.CaptureSourceKind
import org.graphiks.kadre.capture.CaptureTarget
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortReservation
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortTermination
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppKitCapturePortTest {
    @Test
    fun displayCaptureStartsAsBgraAndCopiesTheCallbackLeaseWithinItsBudget() = runBlocking {
        val native = RecordingCaptureNative(
            catalog = AppKitCaptureNativeSourceCatalog(
                displays = listOf(AppKitCaptureNativeDisplaySource(7L, 2, 1, "Primary")),
                windows = emptyList(),
            ),
        )
        val port = AppKitCapturePort(native)
        val snapshot = successValue(port.refreshSources())
        val source = assertIs<CapturePortSources.Enumerated>(snapshot.sources).values.single()
        val reservation = successValue(
            port.reserve(
                CapturePortTarget.Source(CapturePortSourceKey("appkit-display", 7L)),
                CaptureRequest(
                    target = CaptureTarget.HostChoice,
                    preferredFormats = listOf(PixelFormat.Bgra8),
                    cursorMode = CaptureCursorMode.Hidden,
                ),
            ),
        )
        val listener = RecordingStreamListener()

        val started = successValue(reservation.start(listener, maxFrameBytes = 8L))

        assertEquals(source, reservation.source)
        assertEquals(PhysicalSize(2, 1), started.configuration.size)
        assertEquals(PixelFormat.Bgra8, started.configuration.format)
        assertEquals(CaptureCursorMode.Hidden, started.configuration.cursorMode)
        assertEquals(false, native.reservation.configuration?.showsCursor)

        val nativeFrame = RecordingNativeFrame(
            width = 2,
            planes = listOf(AppKitCaptureNativePlane(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 8, 1)),
        )
        native.reservation.emit(nativeFrame)

        val frame = listener.frames.single()
        assertEquals(8, nativeFrame.lastCopyLimit)
        assertEquals(PhysicalSize(2, 1), frame.size)
        assertEquals(PixelFormat.Bgra8, frame.format)
        assertEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).toList(), frame.planes.single().bytes.toList())
        assertEquals(
            ColorEncoding(
                ColorPrimaries.Unknown,
                TransferFunction.Unknown,
                MatrixCoefficients.Unknown,
                ColorRange.Unknown,
                HdrMetadata.Unknown,
            ),
            frame.colorEncoding,
        )
        assertEquals(AlphaMode.Unknown, frame.alphaMode)
    }

    @Test
    fun streamRejectsAnOutputThatCannotFitTheSessionBudgetBeforeStartingNativeCapture() = runBlocking {
        val native = RecordingCaptureNative(
            catalog = AppKitCaptureNativeSourceCatalog(
                displays = listOf(AppKitCaptureNativeDisplaySource(7L, 2, 2, "Primary")),
                windows = emptyList(),
            ),
        )
        val port = AppKitCapturePort(native)
        val reservation = successValue(
            port.reserve(
                CapturePortTarget.Source(CapturePortSourceKey("appkit-display", 7L)),
                CaptureRequest(target = CaptureTarget.HostChoice),
            ),
        )

        val result = reservation.start(RecordingStreamListener(), maxFrameBytes = 15L)

        assertEquals(
            KadreResult.Failure(KadreFailure.ResourceLimitExceeded(KadreResourceKind.CaptureBuffer, 15L)),
            result,
        )
        assertEquals(0, native.reservation.startCalls)
    }
}

private class RecordingCaptureNative(
    private val catalog: AppKitCaptureNativeSourceCatalog,
) : AppKitCaptureNative {
    val reservation = RecordingNativeReservation()

    override fun capability(): AppKitCaptureNativeCapability = AppKitCaptureNativeCapability(
        supportsScreenCapture = true,
        preflightAccessGranted = true,
        supportsHostPicker = true,
    )

    override fun requestPermission(): AppKitCaptureNativePermissionResult = AppKitCaptureNativePermissionResult.Granted

    override fun enumerateSources(
        callback: (AppKitCaptureNativeSourceEnumerationResult) -> Unit,
    ): AutoCloseable {
        callback(AppKitCaptureNativeSourceEnumerationResult.Enumerated(catalog))
        return AutoCloseable { }
    }

    override fun reserve(
        target: AppKitCaptureNativeTarget,
        callback: (AppKitCaptureNativeReservationResult) -> Unit,
    ): AutoCloseable {
        callback(AppKitCaptureNativeReservationResult.Reserved(reservation))
        return AutoCloseable { }
    }
}

private class RecordingNativeReservation : AppKitCaptureNativeReservation {
    override val source: AppKitCaptureNativeReservationSource = AppKitCaptureNativeReservationSource.Display(7L)
    var configuration: AppKitCaptureNativeStreamConfiguration? = null
    var startCalls: Int = 0
    private var onFrame: ((AppKitCaptureNativeFrame) -> Unit)? = null

    override fun start(
        configuration: AppKitCaptureNativeStreamConfiguration,
        onFrame: (AppKitCaptureNativeFrame) -> Unit,
        onOpened: (AppKitCaptureNativeOpenResult) -> Unit,
        onStopped: (AppKitCaptureNativeStopResult) -> Unit,
    ): AutoCloseable {
        this.configuration = configuration
        this.onFrame = onFrame
        startCalls += 1
        onOpened(AppKitCaptureNativeOpenResult.Opened(AppKitCaptureNativeStream { }))
        return AutoCloseable { }
    }

    fun emit(frame: AppKitCaptureNativeFrame) {
        onFrame?.invoke(frame)
    }

    override fun close() = Unit
}

private class RecordingNativeFrame(
    override val width: Int,
    private val planes: List<AppKitCaptureNativePlane>,
) : AppKitCaptureNativeFrame {
    var lastCopyLimit: Int? = null
        private set

    override fun copyPlanes(maxBytes: Int): List<AppKitCaptureNativePlane> {
        lastCopyLimit = maxBytes
        return planes
    }
}

private class RecordingStreamListener : CapturePortStreamListener {
    val frames = mutableListOf<CapturePortFrame>()

    override fun onFrame(frame: CapturePortFrame) {
        frames += frame
    }

    override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) = Unit

    override fun onTerminated(termination: CapturePortTermination) = Unit
}

private fun <T> successValue(result: KadreResult<T>): T = when (result) {
    is KadreResult.Success -> result.value
    is KadreResult.Failure -> error("expected success, got ${result.reason}")
}
