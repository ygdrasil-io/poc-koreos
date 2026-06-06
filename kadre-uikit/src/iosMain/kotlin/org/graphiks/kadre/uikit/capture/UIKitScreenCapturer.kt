package org.graphiks.kadre.uikit.capture

import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.capture.CaptureConfig
import org.graphiks.kadre.core.capture.CapturePermission
import org.graphiks.kadre.core.capture.CaptureSession
import org.graphiks.kadre.core.capture.CaptureSource
import org.graphiks.kadre.core.capture.DisplayInfo
import org.graphiks.kadre.core.capture.ScreenCapturer
import org.graphiks.kadre.core.capture.WindowInfo
import platform.UIKit.UIScreen

class UIKitScreenCapturer : ScreenCapturer {

    override suspend fun enumerateDisplays(): List<DisplayInfo> {
        val screen = UIScreen.mainScreen
        val bounds = screen.bounds
        val scale = screen.scale
        return listOf(
            DisplayInfo(
                id = 0L,
                name = "Main",
                position = PhysicalPosition(bounds.origin.x.toInt(), bounds.origin.y.toInt()),
                resolution = PhysicalSize(
                    (bounds.size.width * scale).toInt(),
                    (bounds.size.height * scale).toInt(),
                ),
                scaleFactor = scale.toDouble(),
            )
        )
    }

    override suspend fun enumerateWindows(): List<WindowInfo> = emptyList()

    override suspend fun createSession(
        source: CaptureSource,
        config: CaptureConfig,
    ): CaptureSession {
        require(source is CaptureSource.Display) {
            "iOS supports display capture only"
        }
        return UIKitCaptureSession(source as CaptureSource.Display, config)
    }

    override suspend fun requestPermission(): CapturePermission = CapturePermission.Pending

    override fun permissionStatus(): CapturePermission = CapturePermission.Pending
}
