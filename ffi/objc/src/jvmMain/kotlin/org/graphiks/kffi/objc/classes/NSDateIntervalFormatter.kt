package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateIntervalFormatter
 * Superclass: NSFormatter
 */
open class NSDateIntervalFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateIntervalFormatter") }
        
    }
    
    fun stringFromDate_toDate(fromDate: MemorySegment, toDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDate:toDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fromDate, toDate) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDate_toDateAsString(fromDate: MemorySegment, toDate: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDate_toDate(fromDate, toDate))
    
    fun stringFromDateInterval(dateInterval: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateInterval) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDateIntervalAsString(dateInterval: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDateInterval(dateInterval))
    
    // @property locale
    fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property calendar
    fun calendar(): MemorySegment {
        val sel = ObjCRuntime.sel("calendar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCalendar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCalendar:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeZone
    fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dateTemplate
    fun dateTemplate(): MemorySegment {
        val sel = ObjCRuntime.sel("dateTemplate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDateTemplate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDateTemplate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun dateTemplateAsString(): String = ObjCRuntime.toJavaString(dateTemplate())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDateTemplate(value: String) = setDateTemplate(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property dateStyle
    fun dateStyle(): NSDateIntervalFormatterStyle {
        val sel = ObjCRuntime.sel("dateStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateIntervalFormatterStyle
    }
    fun setDateStyle(value: NSDateIntervalFormatterStyle) {
        val sel = ObjCRuntime.sel("setDateStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeStyle
    fun timeStyle(): NSDateIntervalFormatterStyle {
        val sel = ObjCRuntime.sel("timeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateIntervalFormatterStyle
    }
    fun setTimeStyle(value: NSDateIntervalFormatterStyle) {
        val sel = ObjCRuntime.sel("setTimeStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

