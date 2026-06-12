package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSISO8601DateFormatter
 * Superclass: NSFormatter
 * Protocols: NSSecureCoding
 */
open class NSISO8601DateFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSISO8601DateFormatter") }
        
        fun stringFromDate_timeZone_formatOptions(date: MemorySegment, timeZone: MemorySegment, formatOptions: NSISO8601DateFormatOptions): MemorySegment {
            val sel = ObjCRuntime.sel("stringFromDate:timeZone:formatOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, date, timeZone, formatOptions) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun stringFromDate_timeZone_formatOptionsAsString(date: MemorySegment, timeZone: MemorySegment, formatOptions: NSISO8601DateFormatOptions): String = ObjCRuntime.toJavaString(stringFromDate_timeZone_formatOptions(date, timeZone, formatOptions))
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun stringFromDate(date: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDateAsString(date: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDate(date))
    
    fun dateFromString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dateFromString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun dateFromString(string: String): MemorySegment = dateFromString(ObjCRuntime.newNSString(Arena.global(), string))
    
    // @property timeZone
    fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formatOptions
    fun formatOptions(): NSISO8601DateFormatOptions {
        val sel = ObjCRuntime.sel("formatOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSISO8601DateFormatOptions
    }
    fun setFormatOptions(value: NSISO8601DateFormatOptions) {
        val sel = ObjCRuntime.sel("setFormatOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _formatter: MemorySegment
    // ivar: _timeZone: MemorySegment
    // ivar: _formatOptions: NSISO8601DateFormatOptions
}

