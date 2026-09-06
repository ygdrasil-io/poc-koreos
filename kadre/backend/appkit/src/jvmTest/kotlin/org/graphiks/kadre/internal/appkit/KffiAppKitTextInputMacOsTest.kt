package org.graphiks.kadre.internal.appkit

import org.graphiks.kffi.objc.NSPoint
import org.graphiks.kffi.objc.NSRange
import org.graphiks.kffi.objc.NSRect
import org.graphiks.kffi.objc.NSSize
import org.graphiks.kffi.objc.managed.ObjCManagedClass
import org.graphiks.kffi.objc.managed.ObjCMethodSignatures
import org.graphiks.kffi.objc.managed.ObjCManagedTextInputValues
import org.graphiks.kffi.objc.managed.ObjCObjectRangeResult
import org.graphiks.kffi.objc.managed.ObjCRectRangeResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Availability proof for the complete managed `NSTextInputClient` callback surface Kadre needs.
 *
 * The ABI-level selector invocations belong to KFFI's native test suite. Kadre consumes only the
 * pointer-free managed router: it may register and revoke each required callback, but it never
 * constructs an Objective-C message or a native struct argument itself.
 */
class KffiAppKitTextInputMacOsTest {
    @Test
    fun publishedKffiRegistersAndRevokesManagedTextInputClientCallbacks() {
        if (!isMacOsTextInputHost()) return

        val methods = mapOf(
            "insertText:replacementRange:" to ObjCMethodSignatures.VoidObjectRange,
            "doCommandBySelector:" to ObjCMethodSignatures.VoidSelector,
            "setMarkedText:selectedRange:replacementRange:" to ObjCMethodSignatures.VoidObjectRangeRange,
            "unmarkText" to ObjCMethodSignatures.Void,
            "selectedRange" to ObjCMethodSignatures.Range,
            "markedRange" to ObjCMethodSignatures.Range,
            "hasMarkedText" to ObjCMethodSignatures.Boolean,
            "attributedSubstringForProposedRange:actualRange:" to ObjCMethodSignatures.ObjectRangeOutRange,
            "validAttributesForMarkedText" to ObjCMethodSignatures.Object,
            "firstRectForCharacterRange:actualRange:" to ObjCMethodSignatures.RectRangeOutRange,
            "characterIndexForPoint:" to ObjCMethodSignatures.ULongPoint,
        )
        val managed = ObjCManagedClass.registerOnce(
            superclassName = "NSView",
            protocols = setOf("NSTextInputClient"),
            methods = methods,
        )
        val values = ObjCManagedTextInputValues()
        val instance = managed.createInstance {
            onVoidObjectRange("insertText:replacementRange:") { _, _ -> }
            onVoidSelector("doCommandBySelector:") { _ -> }
            onVoidObjectRangeRange("setMarkedText:selectedRange:replacementRange:") { _, _, _ -> }
            onVoid("unmarkText") {}
            onRange("selectedRange", fallback = range(0, 0)) { range(2, 3) }
            onRange("markedRange", fallback = range(0, 0)) { range(4, 5) }
            onBoolean("hasMarkedText", fallback = false) { true }
            onObjectRangeOutRange(
                "attributedSubstringForProposedRange:actualRange:",
                fallback = ObjCObjectRangeResult(null, range(0, 0)),
            ) { proposed -> ObjCObjectRangeResult(null, proposed) }
            onObject("validAttributesForMarkedText", fallback = null) { values.markedTextAttributes() }
            onRectRangeOutRange(
                "firstRectForCharacterRange:actualRange:",
                fallback = ObjCRectRangeResult(rect(0.0, 0.0, 0.0, 0.0), range(0, 0)),
            ) { proposed -> ObjCRectRangeResult(rect(1.0, 2.0, 3.0, 4.0), proposed) }
            onULongPoint("characterIndexForPoint:", fallback = 0L) { 9L }
        }

        try {
            instance.close()
            instance.close()
        } finally {
            values.close()
        }
        assertEquals(11, methods.size)
    }
}

private fun range(location: Long, length: Long): NSRange = NSRange(location, length)

private fun rect(x: Double, y: Double, width: Double, height: Double): NSRect =
    NSRect(NSPoint(x, y), NSSize(width, height))

private fun isMacOsTextInputHost(): Boolean = System.getProperty("os.name", "").let { name ->
    name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
}
