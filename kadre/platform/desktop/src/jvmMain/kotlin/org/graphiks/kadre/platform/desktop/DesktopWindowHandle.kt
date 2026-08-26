package org.graphiks.kadre.platform.desktop

import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeDesktopNativeWindowHandle
import org.graphiks.kadre.internal.runtime.RuntimeDesktopWindowHandleAccess
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

/**
 * Runs [block] synchronously on the window's native owner thread under a scoped lifetime lease.
 *
 * The supplied handle and its addresses are borrowed only for the dynamic extent of [block]. They
 * must not be retained or used after [block] returns; callers may return only derived, non-handle
 * data. Closing the window waits for a callback whose lease has already been admitted.
 */
@KadrePlatformApi
@DelicateKadreApi
public suspend fun <R> Window.withDesktopHandle(
    block: (DesktopNativeWindowHandle) -> R,
): KadreResult<R> {
    val access = this as? RuntimeDesktopWindowHandleAccess
        ?: return KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformWindowAccess))
    return access.withDesktopHandle { native ->
        block(
            when (native) {
                is RuntimeDesktopNativeWindowHandle.AppKit -> DesktopNativeWindowHandle.AppKit(
                    native.nsWindowAddress,
                    native.nsViewAddress,
                )
            },
        )
    }
}
