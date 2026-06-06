@file:OptIn(ExperimentalForeignApi::class)

package org.graphiks.kadre.uikit.capture

import kotlinx.cinterop.ExperimentalForeignApi
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.capture.CaptureConfig
import org.graphiks.kadre.core.capture.CaptureError
import org.graphiks.kadre.core.capture.CapturePermission
import org.graphiks.kadre.core.capture.CaptureSession
import org.graphiks.kadre.core.capture.CaptureSource
import org.graphiks.kadre.core.capture.DisplayInfo
import org.graphiks.kadre.core.capture.ScreenCapturer
import org.graphiks.kadre.core.capture.WindowInfo
import platform.UIKit.UIScreen

class UIKitScreenCapturer : ScreenCapturer {

    override suspend fun enumerateDisplays(): List<DisplayInfo> {
        val screen = UIScreen.main
        val bounds = screen.bounds
        val scale = screen.nativeScale
        return bounds.use { rect ->
            listOf(
                DisplayInfo(
                    id = 0L,
                    name = "Main Screen",
                    position = PhysicalPosition(0, 0),
                    resolution = PhysicalSize(
                        rect.size.width.toInt(),
                        rect.size.height.toInt(),
                    ),
                    scaleFactor = scale.toDouble(),
                )
            )
        }
    }

    override suspend fun enumerateWindows(): List<WindowInfo> = emptyList()

    override suspend fun createSession(
        source: CaptureSource,
        config: CaptureConfig,
    ): CaptureSession = when (source) {
        is CaptureSource.Display -> UIKitCaptureSession(source, config)
        is CaptureSource.Window -> throw CaptureError.Unsupported(
            "Window capture not supported on iOS"
        )
    }

    override suspend fun requestPermission(): CapturePermission {
        return CapturePermission.Pending
    }

    override fun permissionStatus(): CapturePermission = CapturePermission.Pending
}
