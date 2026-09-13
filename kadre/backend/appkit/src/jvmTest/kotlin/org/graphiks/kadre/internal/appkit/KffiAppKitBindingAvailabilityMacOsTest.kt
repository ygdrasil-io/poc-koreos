package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.NSApplication
import org.graphiks.kffi.objc.NSColor
import org.graphiks.kffi.objc.NSRequestUserAttentionType
import org.graphiks.kffi.objc.NSWindow
import org.graphiks.kffi.objc.NSWindowSharingType
import org.graphiks.kffi.objc.NSWindowStyleMask
import org.graphiks.kffi.objc.appkit.AppKitWindowGeometryReadResult
import org.graphiks.kffi.objc.appkit.AppKitWindowGeometryServices
import org.graphiks.kffi.objc.appkit.AppKitWindowGeometrySetResult
import org.graphiks.kffi.objc.appkit.DispatchMemoryPressureSource
import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.ObjCRuntime
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the generated KFFI declarations that AppKit window-chrome code consumes.
 *
 * Removing or changing any covered generated declaration must make this macOS proof
 * fail at compilation or native linkage, before production code relies on it.
 */
class KffiAppKitBindingAvailabilityMacOsTest {
    @Test
    fun generatedDispatchMemoryPressureSourceOpensAndClosesWithoutSynthesizingPressureOnMacOs() {
        if (!isMacOsHost()) return

        DispatchMemoryPressureSource { }.close()
    }

    @Test
    fun generatedWindowChromeBindingsCompileAndExecuteSafeReadSetReadChecksOnMacOs() {
        if (!isMacOsHost()) return

        ObjCRuntime.autoreleasePool {
            val application = NSApplication(NSApplication.sharedApplication())
            val window = allocateKffiAppKitTestWindow(
                rect = NSRect(NSPoint(0.0, 0.0), NSSize(160.0, 90.0)),
                style = NSWindowStyleMask.NSWindowStyleMaskBorderless,
            )
            window.setReleasedWhenClosed(false)

            try {
                val attentionRequest = application.requestUserAttention(NSRequestUserAttentionType.NSInformationalRequest)
                try {
                    val initialBackground = window.backgroundColor()
                    assertNotEquals(MemorySegment.NULL, initialBackground)
                    window.setBackgroundColor(NSColor.clearColor())
                    assertNotEquals(MemorySegment.NULL, window.backgroundColor())

                    window.setOpaque(false)
                    assertFalse(window.isOpaque())

                    window.setSharingType(NSWindowSharingType.NSWindowSharingNone)
                    assertEquals(NSWindowSharingType.NSWindowSharingNone, window.sharingType())

                    @Suppress("UNUSED_VARIABLE")
                    val dragBinding: (MemorySegment) -> Unit = window::performWindowDragWithEvent
                } finally {
                    application.cancelUserAttentionRequest(attentionRequest)
                }
            } finally {
                try {
                    window.close()
                } finally {
                    releaseKffiAppKitTestObject(window.ptr)
                }
            }
        }
    }

    @Test
    fun generatedWindowGeometryServiceReportsOnlyRealWindowServerReadbackOrUnavailabilityOnMacOs26() {
        if (!isMacOsHost() || !AppKitDisplayAvailability().isAvailable) return

        ObjCRuntime.autoreleasePool {
            NSApplication(NSApplication.sharedApplication())
            val window = allocateKffiAppKitTestWindow(
                rect = NSRect(NSPoint(80.0, 80.0), NSSize(160.0, 90.0)),
                style = NSWindowStyleMask.NSWindowStyleMaskBorderless,
            )
            window.setReleasedWhenClosed(false)

            try {
                window.makeKeyAndOrderFront(MemorySegment.NULL)
                when (val initial = AppKitWindowGeometryServices.readOuterBounds(window)) {
                    is AppKitWindowGeometryReadResult.Read -> {
                        assertTrue(initial.bounds.width > 0.0)
                        assertTrue(initial.bounds.height > 0.0)

                        val moved = assertIs<AppKitWindowGeometrySetResult.Moved>(
                            AppKitWindowGeometryServices.setOuterPosition(
                                window,
                                initial.bounds.x.toInt(),
                                initial.bounds.y.toInt(),
                            ),
                        )
                        assertEquals(
                            moved.bounds,
                            assertIs<AppKitWindowGeometryReadResult.Read>(
                                AppKitWindowGeometryServices.readOuterBounds(window),
                            ).bounds,
                        )
                    }

                    AppKitWindowGeometryReadResult.Unavailable -> Unit
                    else -> error("unexpected generated geometry readback result: $initial")
                }
            } finally {
                try {
                    window.close()
                } finally {
                    releaseKffiAppKitTestObject(window.ptr)
                }
            }
        }
    }
}

private fun isMacOsHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
