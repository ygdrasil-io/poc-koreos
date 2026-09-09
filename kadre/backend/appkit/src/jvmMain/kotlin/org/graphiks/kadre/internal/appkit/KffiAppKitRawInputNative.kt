@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.graphiks.kffi.objc.CGEventField
import org.graphiks.kffi.objc.CGEventType
import org.graphiks.kffi.objc.CGRequestListenEventAccess
import org.graphiks.kffi.objc.appkit.CGListenOnlyEventTap
import org.graphiks.kffi.objc.appkit.EventTapPermissionState
import org.graphiks.kffi.objc.managed.BorrowedCGEvent

/** KFFI-only CoreGraphics adapter; it copies no native pointer beyond the callback extent. */
internal object KffiAppKitRawInputNative : AppKitRawInputNative {
    override fun preflightPermission(): Boolean = KffiAppKitMainThread.call {
        CGListenOnlyEventTap.preflight() == EventTapPermissionState.Granted
    }

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Default) {
        KffiAppKitMainThread.call(::CGRequestListenEventAccess)
    }

    override fun installTap(
        listener: (AppKitRawInputNativeEvent) -> Unit,
    ): AppKitRawInputNativeTap = KffiAppKitMainThread.call {
        KffiAppKitRawInputTap(
            CGListenOnlyEventTap.install(MOUSE_MOTION_MASK) { type, event ->
                listener(type.toRawInputEvent(event))
            },
        )
    }

    private fun CGEventType.toRawInputEvent(event: BorrowedCGEvent): AppKitRawInputNativeEvent = when (this) {
        CGEventType.kCGEventMouseMoved,
        CGEventType.kCGEventLeftMouseDragged,
        CGEventType.kCGEventRightMouseDragged,
        CGEventType.kCGEventOtherMouseDragged,
        -> AppKitRawInputNativeEvent.Motion(
            deltaX = event.integerValue(CGEventField.kCGMouseEventDeltaX),
            deltaY = event.integerValue(CGEventField.kCGMouseEventDeltaY),
        )

        CGEventType.kCGEventTapDisabledByTimeout -> AppKitRawInputNativeEvent.DisabledByTimeout
        CGEventType.kCGEventTapDisabledByUserInput -> AppKitRawInputNativeEvent.DisabledByUserInput
        else -> error("raw-input tap received an event outside its installed mask: $this")
    }
}

private class KffiAppKitRawInputTap(
    private val tap: CGListenOnlyEventTap,
) : AppKitRawInputNativeTap {
    override fun reenable() = KffiAppKitMainThread.call(tap::reenable)

    override fun close() = KffiAppKitMainThread.call(tap::close)
}

private val MOUSE_MOTION_MASK: Long = listOf(
    CGEventType.kCGEventMouseMoved,
    CGEventType.kCGEventLeftMouseDragged,
    CGEventType.kCGEventRightMouseDragged,
    CGEventType.kCGEventOtherMouseDragged,
).fold(0L) { mask, type ->
    mask or (1L shl type.value.toInt())
}
