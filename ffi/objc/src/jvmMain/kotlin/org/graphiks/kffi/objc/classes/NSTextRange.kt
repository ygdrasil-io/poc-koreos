package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextRange
 * Superclass: NSObject
 */
open class NSTextRange(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextRange") }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithLocation_endLocation(location: MemorySegment, endLocation: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocation:endLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, endLocation) as MemorySegment
    }
    
    open fun initWithLocation(location: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun isEqualToTextRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    open fun containsLocation(location: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, location) as BOOL
    }
    
    open fun containsRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    open fun intersectsWithTextRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    open fun textRangeByIntersectingWithTextRange(textRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textRangeByIntersectingWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRange) as MemorySegment
    }
    
    open fun textRangeByFormingUnionWithTextRange(textRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textRangeByFormingUnionWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRange) as MemorySegment
    }
    
    // @property empty
    open fun isEmpty(): BOOL {
        val sel = ObjCRuntime.sel("isEmpty")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property location
    /** @return id<NSTextLocation> */
    open fun location(): MemorySegment {
        val sel = ObjCRuntime.sel("location")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property endLocation
    /** @return id<NSTextLocation> */
    open fun endLocation(): MemorySegment {
        val sel = ObjCRuntime.sel("endLocation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

