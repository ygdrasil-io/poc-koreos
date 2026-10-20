package org.graphiks.kadre.internal.linux.pipewire

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kadre.capture.CaptureOutcome
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortStreamListener
import org.graphiks.kadre.internal.runtime.CapturePortStreamStart
import org.graphiks.kadre.internal.runtime.CapturePortTermination

/**
 * O2 proofs for the PipeWire stream client (design
 * LINUX-PIPEWIRE-STREAM-DESIGN D9/D11/D12): the negotiation, the frame
 * discipline and the teardown all run against a scripted native seam, no
 * libpipewire involved. These are the portable half of contract CAP-004.
 */
internal class PipeWireStreamClientTest {

    private val objectFormat = 262147
    private val paramFormat = 4
    private val paramBuffers = 5
    private val keyMediaType = 1
    private val keyMediaSubtype = 2
    private val keyVideoFormat = 131073
    private val keyVideoSize = 131075
    private val keyVideoFramerate = 131076
    private val keyBuffersBlocks = 2
    private val keyBuffersStride = 4
    private val spaBgrx = 8
    private val spaBgra = 12
    private val spaMediaVideo = 2
    private val spaSubtypeRaw = 1
    private val dataTypeMemPtr = 1

    private class FakeSession : PipeWireNativeSession {
        var state = PipeWireStreamStates.UNCONNECTED
        var closed = false
        val bufferProposals = mutableListOf<IntArray>() // count, blocks, size, stride (-1 = null)
        var nextFrame: (() -> List<PolledPlane>?)? = null

        override fun awaitStateChange(currentState: Int, timeoutMillis: Long): Int {
            Thread.sleep(minOf(timeoutMillis, 25))
            return state
        }

        override fun currentState(): Int = state

        override fun updateBufferParams(count: Int, blocks: Int?, size: Int?, stride: Int?) {
            bufferProposals += intArrayOf(count, blocks ?: -1, size ?: -1, stride ?: -1)
        }

        override fun pollFrame(): List<PolledPlane>? = nextFrame?.invoke()

        override fun close() {
            closed = true
        }
    }

    private fun awaitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not reached within ${timeoutMillis}ms" }
            Thread.sleep(10)
        }
    }

    private fun started(result: org.graphiks.kadre.diagnostics.KadreResult<CapturePortStreamStart>): CapturePortStreamStart {
        val value = (result as? org.graphiks.kadre.diagnostics.KadreResult.Success<*>)?.value
        return value as CapturePortStreamStart
    }

    private fun failed(result: org.graphiks.kadre.diagnostics.KadreResult<CapturePortStreamStart>): KadreFailure =
        (result as org.graphiks.kadre.diagnostics.KadreResult.Failure).reason

    private class RecordingListener : CapturePortStreamListener {
        val frames = mutableListOf<CapturePortFrame>()
        val reconfigurations = mutableListOf<org.graphiks.kadre.capture.CaptureConfiguration>()
        val terminations = mutableListOf<CapturePortTermination>()
        val frameGate = CountDownLatch(1)

        override fun onFrame(frame: CapturePortFrame) {
            frames += frame
        }

        override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) {
            reconfigurations += configuration
        }

        override fun onTerminated(termination: CapturePortTermination) {
            terminations += termination
        }
    }

    private fun formatPod(
        spaFormat: Int,
        width: Int,
        height: Int,
        framesPerSecond: Int = 25,
    ): ByteArray = SpaPodWriter.toByteArray(
        SpaValue.ObjectValue(
            objectType = objectFormat,
            id = paramFormat,
            properties = listOf(
                SpaValue.PropValue(keyMediaType, 0, SpaValue.IdValue(spaMediaVideo)),
                SpaValue.PropValue(keyMediaSubtype, 0, SpaValue.IdValue(spaSubtypeRaw)),
                SpaValue.PropValue(keyVideoFormat, 0, SpaValue.IdValue(spaFormat)),
                SpaValue.PropValue(keyVideoSize, 0, SpaValue.RectangleValue(width, height)),
                SpaValue.PropValue(keyVideoFramerate, 0, SpaValue.FractionValue(framesPerSecond, 1)),
            ),
        ),
    )

    private fun buffersPod(blocks: Int, stride: Int): ByteArray = SpaPodWriter.toByteArray(
        SpaValue.ObjectValue(
            objectType = 262148,
            id = paramBuffers,
            properties = listOf(
                SpaValue.PropValue(keyBuffersBlocks, 0, SpaValue.IntValue(blocks)),
                SpaValue.PropValue(keyBuffersStride, 0, SpaValue.IntValue(stride)),
            ),
        ),
    )

    private fun bgrxPlane(width: Int, height: Int, stride: Int, fill: Byte): PolledPlane {
        val bytes = ByteArray(stride * height) { fill }
        return PolledPlane(dataTypeMemPtr, 0, stride * height, stride, bytes)
    }

    @Test
    fun negotiationReachesStreamingWithTheEffectiveConfiguration() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.CONNECTING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 64, 48))
            it.events.onParamChanged(paramBuffers, buffersPod(blocks = 1, stride = 256))
            session.state = PipeWireStreamStates.STREAMING
            val listener = RecordingListener()
            val start = started(it.start(listener, maxFrameBytes = 1 shl 20, timeoutMillis = 1_000))
            val configuration = start.configuration
            assertEquals(PixelFormat.Bgrx8, configuration.format)
            assertEquals(64, configuration.size.width)
            assertEquals(48, configuration.size.height)
            assertEquals(0L, configuration.revision.value)
            // the client answered the Format with a packed buffer proposal
            assertEquals(1, session.bufferProposals.size)
            kotlin.test.assertContentEquals(intArrayOf(2, 1, 256 * 48, 256), session.bufferProposals[0])
        }
    }

    @Test
    fun framesArriveDetachedWithExactLayouts() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            it.events.onParamChanged(paramBuffers, buffersPod(blocks = 1, stride = 128))
            val listener = RecordingListener()
            started(it.start(listener, maxFrameBytes = 1 shl 20, timeoutMillis = 1_000))
            // feed a frame through the process path
            val plane = bgrxPlane(32, 16, 128, 0x5a)
            session.nextFrame = { listOf(plane) }
            it.events.onProcess()
            awaitUntil { listener.frames.isNotEmpty() }
            val frame = listener.frames.last()
            assertEquals(1, frame.planes.size)
            assertEquals(128 * 16, frame.planes[0].layout.byteCount)
            assertEquals(128, frame.planes[0].layout.rowStride)
            assertTrue(frame.planes[0].bytes.all { b -> b == 0x5a.toByte() })
        }
    }

    @Test
    fun slowConsumersDropCountedFramesInsteadOfBlockingTheLoop() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            it.events.onParamChanged(paramBuffers, buffersPod(blocks = 1, stride = 128))
            val gate = CountDownLatch(1)
            val firstDelivered = CountDownLatch(1)
            val received = ArrayList<CapturePortFrame>()
            val listener = object : CapturePortStreamListener {
                override fun onFrame(frame: CapturePortFrame) {
                    synchronized(received) { received += frame }
                    firstDelivered.countDown()
                    gate.await()
                }

                override fun onReconfigured(configuration: org.graphiks.kadre.capture.CaptureConfiguration) = Unit

                override fun onTerminated(termination: CapturePortTermination) = Unit
            }
            started(it.start(listener, 1 shl 20, 1_000))
            var counter: Byte = 1
            session.nextFrame = {
                counter++
                listOf(bgrxPlane(32, 16, 128, counter))
            }
            // frame 1 parks the drainer inside onFrame; while it is parked,
            // capacity keeps three queued and the rest are counted drops
            it.events.onProcess()
            assertTrue(firstDelivered.await(2, TimeUnit.SECONDS), "the first frame must flow")
            repeat(6) { _ -> client.events.onProcess() }
            assertEquals(3, it.droppedFrameCount(), "overflow frames must be counted drops")
            gate.countDown()
            awaitUntil { synchronized(received) { received.size } == 4 }
            val deliveredCount = synchronized(received) { received.size }
            assertEquals(4, deliveredCount, "one in flight + capacity three, no more")
        }
    }

    @Test
    fun overBudgetFramesTerminateWithTheResourceLimit() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            it.events.onParamChanged(paramBuffers, buffersPod(blocks = 1, stride = 128))
            val listener = RecordingListener()
            started(it.start(listener, maxFrameBytes = 100, timeoutMillis = 1_000))
            session.nextFrame = { listOf(bgrxPlane(32, 16, 128, 0)) }
            it.events.onProcess()
            assertTrue(listener.frames.isEmpty(), "no frame may be delivered over budget")
            awaitUntil { listener.terminations.isNotEmpty() }
            val outcome = (listener.terminations.last() as CapturePortTermination.Outcome).outcome
            val failure = (outcome as CaptureOutcome.Failed).failure
            assertTrue(failure is KadreFailure.ResourceLimitExceeded)
            assertEquals(KadreResourceKind.CaptureBuffer, (failure as KadreFailure.ResourceLimitExceeded).resource)
        }
    }

    @Test
    fun formatsOutsideThePortableSubsetAreRefused() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.CONNECTING
            it.events.onParamChanged(paramFormat, formatPod(99, 64, 48))
            val failure = failed(it.start(RecordingListener(), 1 shl 20, 200))
            assertEquals("format-unsupported", (failure as KadreFailure.PlatformFailure).code)
        }
    }

    @Test
    fun formatChangesRepublishAnIncrementedConfiguration() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            val listener = RecordingListener()
            started(it.start(listener, 1 shl 20, 1_000))
            it.events.onParamChanged(paramFormat, formatPod(spaBgra, 32, 16))
            assertEquals(1, listener.reconfigurations.size)
            assertEquals(PixelFormat.Bgra8, listener.reconfigurations.last().format)
            assertEquals(1L, listener.reconfigurations.last().revision.value)
        }
    }

    @Test
    fun sourceLossAfterStreamingTerminatesHonestly() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            val listener = RecordingListener()
            started(it.start(listener, 1 shl 20, 1_000))
            it.events.onStateChanged(PipeWireStreamStates.UNCONNECTED, null)
            awaitUntil { listener.terminations.any { it is CapturePortTermination.SourceLost } }
        }
    }

    @Test
    fun layoutViolationsRefuseTheFrame() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        client.use {
            session.state = PipeWireStreamStates.STREAMING
            it.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
            it.events.onParamChanged(paramBuffers, buffersPod(blocks = 1, stride = 128))
            val listener = RecordingListener()
            started(it.start(listener, 1 shl 20, 1_000))
            // a chunk smaller than the negotiated layout is not a frame
            session.nextFrame = { listOf(PolledPlane(dataTypeMemPtr, 0, 16, 128, ByteArray(16))) }
            it.events.onProcess()
            awaitUntil { listener.terminations.isNotEmpty() }
            assertTrue(listener.frames.isEmpty())
            val outcome = (listener.terminations.last() as CapturePortTermination.Outcome).outcome
            assertEquals(
                "frame-layout-invalid",
                ((outcome as CaptureOutcome.Failed).failure as KadreFailure.PlatformFailure).code,
            )
        }
    }

    @Test
    fun negotiationTimeoutStaysBounded() {
        val session = FakeSession()
        session.state = PipeWireStreamStates.CONNECTING
        val client = PipeWireStreamClient(session)
        client.use {
            val started = System.currentTimeMillis()
            val result = it.start(RecordingListener(), 1 shl 20, 150)
            val elapsed = System.currentTimeMillis() - started
            assertEquals("negotiation-timeout", (failed(result) as KadreFailure.PlatformFailure).code)
            assertTrue(elapsed < 2_000, "waits must honor the budget (took ${elapsed}ms)")
        }
    }

    @Test
    fun teardownIsIdempotentAndSilencesLateEvents() {
        val session = FakeSession()
        val client = PipeWireStreamClient(session)
        session.state = PipeWireStreamStates.STREAMING
        client.events.onParamChanged(paramFormat, formatPod(spaBgrx, 32, 16))
        val listener = RecordingListener()
        started(client.start(listener, 1 shl 20, 1_000))
        client.close()
        client.close()
        assertTrue(session.closed, "the native session must close exactly once visibly")
        client.events.onProcess()
        client.events.onParamChanged(paramFormat, formatPod(spaBgra, 32, 16))
        Thread.sleep(50)
        assertEquals(0, listener.frames.size, "no frame after close")
    }
}
