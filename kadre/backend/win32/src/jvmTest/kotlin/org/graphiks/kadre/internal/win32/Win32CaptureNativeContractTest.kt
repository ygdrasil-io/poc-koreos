package org.graphiks.kadre.internal.win32

import kotlin.time.Duration.Companion.seconds
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.capture.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.*

/**
 * Real session through the real provider: the capture manager enumerates the
 * live monitors honestly, a monitor capture streams real BGRA8 frames through
 * the bounded copy, and collectFrames terminates cleanly on requestStop. On a
 * host without Windows Graphics Capture the enumeration must be honestly
 * unavailable instead of silently empty. Multi-monitor topologies and hotplug
 * stay manual receptions.
 */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
class Win32CaptureNativeContractTest {
    @Test fun liveMonitorCaptureStreamsRealFrames() {
        runKadreApplication(
            options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                windows.requestWindow(WindowSpec(title = "Kadre native capture proof",
                    contentSize = LogicalSize(320.0, 240.0)))
                val state = withTimeout(10.seconds) {
                    capture.state.first { controlPlane ->
                        val sources = controlPlane.sources
                        sources is CaptureSources.Enumerated || sources is CaptureSources.Unavailable
                    }
                }
                val inventory = state.sources
                when (inventory) {
                    is CaptureSources.Enumerated -> {
                        assertTrue(inventory.values.isNotEmpty(), "at least one monitor must enumerate")
                        val source = inventory.values.first()
                        val opened = capture.open(
                            CaptureRequest(target = CaptureTarget.Source(source.id, state.revision)),
                        )
                        val session = assertIs<KadreResult.Success<CaptureSession>>(opened).value
                        var frames = 0
                        val collected = session.collectFrames { frame ->
                            frames++
                            assertTrue(frame.size.width > 0 && frame.size.height > 0)
                            assertEquals(PixelFormat.Bgra8, frame.format)
                            if (frames == 3) requestStop()
                        }
                        assertIs<KadreResult.Success<Unit>>(collected)
                        assertTrue(frames >= 1, "at least one frame must stream")
                        assertEquals(CaptureOutcome.Stopped(CaptureStopReason.Requested), session.awaitTermination())
                    }
                    is CaptureSources.Unavailable -> {
                        assertIs<KadreFailure.PlatformFailure>(inventory.failure)
                    }
                    else -> error("honest control plane required, got: $inventory")
                }
            },
        )
    }
}
