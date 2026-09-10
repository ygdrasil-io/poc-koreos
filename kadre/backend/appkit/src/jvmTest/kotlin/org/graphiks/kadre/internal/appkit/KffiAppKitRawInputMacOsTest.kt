@file:OptIn(org.graphiks.kffi.objc.PlatformAvailability::class)

package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.appkit.CGListenOnlyEventTap
import org.graphiks.kffi.objc.CGEventType
import org.graphiks.kffi.objc.appkit.EventTapPermissionState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Native-linkage proof for the generated listen-only preflight used by the AppKit raw-input
 * adapter. It intentionally neither requests Input Monitoring nor installs a global event tap.
 */
class KffiAppKitRawInputMacOsTest {
    @Test
    fun rawInputMaskExcludesKeyboardAndModifierEvents() {
        val pointerMotionMask = eventMask(
            CGEventType.kCGEventMouseMoved,
            CGEventType.kCGEventLeftMouseDragged,
            CGEventType.kCGEventRightMouseDragged,
            CGEventType.kCGEventOtherMouseDragged,
        )
        val keyboardMask = eventMask(
            CGEventType.kCGEventKeyDown,
            CGEventType.kCGEventKeyUp,
            CGEventType.kCGEventFlagsChanged,
        )

        assertEquals(pointerMotionMask, APPKIT_RAW_INPUT_EVENT_MASK)
        assertEquals(0L, APPKIT_RAW_INPUT_EVENT_MASK and keyboardMask)
    }

    @Test
    fun generatedListenOnlyPreflightMatchesTheAdapterPermissionReadbackOnMacOs() {
        if (!isMacOsRawInputHost()) return

        val expected = KffiAppKitMainThread.call {
            CGListenOnlyEventTap.preflight() == EventTapPermissionState.Granted
        }

        assertEquals(expected, KffiAppKitRawInputNative.preflightPermission())
    }
}

private fun eventMask(vararg types: CGEventType): Long =
    types.fold(0L) { mask, type -> mask or (1L shl type.value.toInt()) }

private fun isMacOsRawInputHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
