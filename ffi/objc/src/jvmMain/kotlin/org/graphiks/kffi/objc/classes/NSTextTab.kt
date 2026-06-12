package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextTab
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding, NSSecureCoding
 */
open class NSTextTab(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextTab") }
        
        open fun columnTerminatorsForLocale(aLocale: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("columnTerminatorsForLocale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, aLocale) as MemorySegment
        }
        
    }
    
    // @property location
    open fun location(): CGFloat {
        val sel = ObjCRuntime.sel("location")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property options
    /** @return NSDictionary<NSTextTabOptionKey,id> * */
    open fun options(): MemorySegment {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category:  on NSTextTab ─────────────────────────────────────────

fun NSTextTab.initWithTextAlignment_location_options(alignment: NSTextAlignment, loc: CGFloat, options: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTextAlignment:location:options:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, alignment, loc, options) as MemorySegment
}

fun NSTextTab.alignment(): NSTextAlignment {
    val sel = ObjCRuntime.sel("alignment")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
}

// @property alignment
fun NSTextTab.initWithType_location(type: NSTextTabType, loc: CGFloat): MemorySegment {
    val sel = ObjCRuntime.sel("initWithType:location:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type, loc) as MemorySegment
}

fun NSTextTab.tabStopType(): NSTextTabType {
    val sel = ObjCRuntime.sel("tabStopType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextTabType
}

// @property tabStopType