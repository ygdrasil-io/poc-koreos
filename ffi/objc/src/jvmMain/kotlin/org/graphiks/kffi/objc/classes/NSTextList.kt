package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextList
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSTextList(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextList") }
        
        open fun includesTextListMarkers(): BOOL {
            val sel = ObjCRuntime.sel("includesTextListMarkers")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun initWithMarkerFormat_options_startingItemNumber(markerFormat: NSTextListMarkerFormat, options: NSTextListOptions, startingItemNumber: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMarkerFormat:options:startingItemNumber:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, markerFormat, options, startingItemNumber) as MemorySegment
    }
    
    open fun initWithMarkerFormat_options(markerFormat: NSTextListMarkerFormat, options: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMarkerFormat:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, markerFormat, options) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun markerForItemNumber(itemNumber: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("markerForItemNumber:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemNumber) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun markerForItemNumberAsString(itemNumber: NSInteger): String = ObjCRuntime.toJavaString(markerForItemNumber(itemNumber))
    
    // @property markerFormat
    open fun markerFormat(): NSTextListMarkerFormat {
        val sel = ObjCRuntime.sel("markerFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextListMarkerFormat
    }
    
    // @property listOptions
    open fun listOptions(): NSTextListOptions {
        val sel = ObjCRuntime.sel("listOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextListOptions
    }
    
    // @property startingItemNumber
    open fun startingItemNumber(): NSInteger {
        val sel = ObjCRuntime.sel("startingItemNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setStartingItemNumber(value: NSInteger) {
        val sel = ObjCRuntime.sel("setStartingItemNumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property ordered
    open fun isOrdered(): BOOL {
        val sel = ObjCRuntime.sel("isOrdered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property includesTextListMarkers
    open fun includesTextListMarkers(): BOOL {
        val sel = ObjCRuntime.sel("includesTextListMarkers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

