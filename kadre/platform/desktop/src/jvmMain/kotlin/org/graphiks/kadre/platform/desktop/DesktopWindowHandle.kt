package org.graphiks.kadre.platform.desktop

import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.window.Window

public sealed interface DesktopNativeWindowHandle {
    public data class AppKit internal constructor(
        public val nsWindowAddress: ULong,
        public val nsViewAddress: ULong,
    ) : DesktopNativeWindowHandle

    public data class Win32 internal constructor(public val hwnd: ULong) : DesktopNativeWindowHandle

    public data class X11 internal constructor(
        public val displayAddress: ULong,
        public val window: ULong,
    ) : DesktopNativeWindowHandle

    public data class Wayland internal constructor(
        public val displayAddress: ULong,
        public val surfaceAddress: ULong,
    ) : DesktopNativeWindowHandle
}

@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> Window.withDesktopHandle(
    block: (DesktopNativeWindowHandle) -> R,
): KadreResult<R> = KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess))
