package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScreen
 * Superclass: NSObject
 */
open class NSScreen(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScreen") }
        
        /** @return NSArray<NSScreen *> * */
        open fun screens(): MemorySegment {
            val sel = ObjCRuntime.sel("screens")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun mainScreen(): MemorySegment {
            val sel = ObjCRuntime.sel("mainScreen")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun deepestScreen(): MemorySegment {
            val sel = ObjCRuntime.sel("deepestScreen")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun screensHaveSeparateSpaces(): BOOL {
            val sel = ObjCRuntime.sel("screensHaveSeparateSpaces")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun canRepresentDisplayGamut(displayGamut: NSDisplayGamut): BOOL {
        val sel = ObjCRuntime.sel("canRepresentDisplayGamut:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, displayGamut) as BOOL
    }
    
    open fun convertRectToBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectToBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    open fun convertRectFromBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectFromBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    open fun backingAlignedRect_options(rect: NSRect, options: NSAlignmentOptions): NSRect {
        val sel = ObjCRuntime.sel("backingAlignedRect:options:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), options) as NSRect
    }
    
    // @property screens
    /** @return NSArray<NSScreen *> * */
    open fun screens(): MemorySegment {
        val sel = ObjCRuntime.sel("screens")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property mainScreen
    open fun mainScreen(): MemorySegment {
        val sel = ObjCRuntime.sel("mainScreen")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deepestScreen
    open fun deepestScreen(): MemorySegment {
        val sel = ObjCRuntime.sel("deepestScreen")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property screensHaveSeparateSpaces
    open fun screensHaveSeparateSpaces(): BOOL {
        val sel = ObjCRuntime.sel("screensHaveSeparateSpaces")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property depth
    open fun depth(): NSWindowDepth {
        val sel = ObjCRuntime.sel("depth")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowDepth
    }
    
    // @property frame
    open fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property visibleFrame
    open fun visibleFrame(): NSRect {
        val sel = ObjCRuntime.sel("visibleFrame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property deviceDescription
    /** @return NSDictionary<NSDeviceDescriptionKey,id> * */
    open fun deviceDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property colorSpace
    open fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property supportedWindowDepths
    open fun supportedWindowDepths(): MemorySegment {
        val sel = ObjCRuntime.sel("supportedWindowDepths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property backingScaleFactor
    open fun backingScaleFactor(): CGFloat {
        val sel = ObjCRuntime.sel("backingScaleFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property localizedName
    open fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property safeAreaInsets
    open fun safeAreaInsets(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("safeAreaInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    
    // @property auxiliaryTopLeftArea
    open fun auxiliaryTopLeftArea(): NSRect {
        val sel = ObjCRuntime.sel("auxiliaryTopLeftArea")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property auxiliaryTopRightArea
    open fun auxiliaryTopRightArea(): NSRect {
        val sel = ObjCRuntime.sel("auxiliaryTopRightArea")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property CGDirectDisplayID
    open fun CGDirectDisplayID(): CGDirectDisplayID {
        val sel = ObjCRuntime.sel("CGDirectDisplayID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as CGDirectDisplayID
    }
    
}

// ── Category:  on NSScreen ─────────────────────────────────────────

fun NSScreen.maximumExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSScreen.maximumPotentialExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumPotentialExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSScreen.maximumReferenceExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumReferenceExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property maximumExtendedDynamicRangeColorComponentValue
fun NSScreen.maximumExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property maximumPotentialExtendedDynamicRangeColorComponentValue
fun NSScreen.maximumPotentialExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumPotentialExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property maximumReferenceExtendedDynamicRangeColorComponentValue
fun NSScreen.maximumReferenceExtendedDynamicRangeColorComponentValue(): CGFloat {
    val sel = ObjCRuntime.sel("maximumReferenceExtendedDynamicRangeColorComponentValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// ── Category: NSDisplayLink on NSScreen ─────────────────────────────────────────

fun NSScreen.displayLinkWithTarget_selector(target: MemorySegment, selector: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("displayLinkWithTarget:selector:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, selector) as MemorySegment
}

// ── Category: NSDeprecated on NSScreen ─────────────────────────────────────────

fun NSScreen.userSpaceScaleFactor(): CGFloat {
    val sel = ObjCRuntime.sel("userSpaceScaleFactor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

