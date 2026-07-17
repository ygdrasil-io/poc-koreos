package org.graphiks.kadre.samples.hellotriangle

import io.ygdrasil.webgpu.WGPUInstanceBackend
import org.graphiks.kadre.core.RawWindowHandle

internal sealed interface InteractiveSurfaceTarget {
    data class AppKit(
        val nsView: Long,
        val nsLayer: Long,
        val backend: WGPUInstanceBackend = WGPUInstanceBackend.Metal,
    ) : InteractiveSurfaceTarget

    data class Win32(
        val hwnd: Long,
        val hinstance: Long,
        val backend: WGPUInstanceBackend = WGPUInstanceBackend.Primary,
    ) : InteractiveSurfaceTarget

    data object Unsupported : InteractiveSurfaceTarget
}

internal fun interactiveSurfaceTarget(handle: RawWindowHandle): InteractiveSurfaceTarget = when (handle) {
    is RawWindowHandle.AppKit -> InteractiveSurfaceTarget.AppKit(
        nsView = handle.nsView,
        nsLayer = handle.nsLayer,
    )
    is RawWindowHandle.Win32 -> InteractiveSurfaceTarget.Win32(
        hwnd = handle.hwnd,
        hinstance = handle.hinstance,
    )
    else -> InteractiveSurfaceTarget.Unsupported
}
