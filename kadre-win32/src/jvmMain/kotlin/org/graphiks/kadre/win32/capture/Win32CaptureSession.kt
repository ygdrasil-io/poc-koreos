package org.graphiks.kadre.win32.capture

import org.graphiks.kadre.core.capture.CaptureConfig
import org.graphiks.kadre.core.capture.CaptureFrame
import org.graphiks.kadre.core.capture.CaptureSession
import org.graphiks.kadre.core.capture.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun win32CapturePeriodMillis(frameRate: Int): Long {
    require(frameRate in 1..1000) {
        "Windows capture frameRate must be in 1..1000, got $frameRate"
    }
    return 1000L / frameRate
}

class Win32CaptureSession(
    source: CaptureSource,
    config: CaptureConfig,
    private val hwnd: Long?,
    rect: Win32MonitorRect?,
) : CaptureSession(source, config) {

    private val capturePeriodMillis = win32CapturePeriodMillis(config.frameRate)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val duplicator: AutoCloseable? = when (source) {
        is CaptureSource.Display -> {
            if (rect != null) DxgiOutputDuplicator(source.id, rect) else null
        }
        is CaptureSource.Window -> {
            if (hwnd != null) GdiWindowCapture(hwnd) else null
        }
    }

    init {
        scope.launch {
            while (isActive) {
                val frame = captureFrame()
                if (frame != null) {
                    _frames.tryEmit(frame)
                }
                delay(capturePeriodMillis)
            }
        }
    }

    private fun captureFrame(): CaptureFrame? {
        return when (source) {
            is CaptureSource.Display -> {
                val dup = duplicator as? DxgiOutputDuplicator ?: return null
                dup.acquireFrame(1000L)
            }
            is CaptureSource.Window -> {
                val capture = duplicator as? GdiWindowCapture ?: return null
                capture.acquireFrame()
            }
        }
    }

    override fun close() {
        scope.cancel()
        duplicator?.close()
    }
}
