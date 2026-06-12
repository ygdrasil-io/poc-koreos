package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableString
 * Superclass: NSString
 */
open class NSMutableString(ptr: MemorySegment) : NSString(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableString") }
        
    }
    
    fun replaceCharactersInRange_withString(range: NSRange, aString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceCharactersInRange:withString:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), aString)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun replaceCharactersInRange_withString(range: NSRange, aString: String): Unit = replaceCharactersInRange_withString(range, ObjCRuntime.newNSString(Arena.global(), aString))
    
}

// ── Category: NSMutableStringExtensionMethods on NSMutableString ─────────────────────────────────────────

fun NSMutableString.insertString_atIndex(aString: MemorySegment, loc: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("insertString:atIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, aString, loc)
}

fun NSMutableString.deleteCharactersInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("deleteCharactersInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableString.appendString(aString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendString:")
    ObjCRuntime.msgSend(null, ptr, sel, aString)
}

fun NSMutableString.appendFormat(format: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, format)
}

fun NSMutableString.setString(aString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setString:")
    ObjCRuntime.msgSend(null, ptr, sel, aString)
}

fun NSMutableString.replaceOccurrencesOfString_withString_options_range(target: MemorySegment, replacement: MemorySegment, options: NSStringCompareOptions, searchRange: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("replaceOccurrencesOfString:withString:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, target, replacement, options, searchRange) as NSUInteger
}

fun NSMutableString.applyTransform_reverse_range_updatedRange(transform: NSStringTransform, reverse: BOOL, range: NSRange, resultingRange: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("applyTransform:reverse:range:updatedRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, transform, reverse, range, resultingRange) as BOOL
}

fun NSMutableString.initWithCapacity(capacity: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCapacity:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, capacity) as MemorySegment
}

// Class<*> method: +[NSMutableString stringWithCapacity:]
fun NSMutableString_stringWithCapacity(capacity: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithCapacity:")
    val cls = ObjCRuntime.getClass("NSMutableString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, capacity) as MemorySegment
}

