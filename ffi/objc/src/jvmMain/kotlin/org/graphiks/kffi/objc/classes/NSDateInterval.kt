package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateInterval
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSDateInterval(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateInterval") }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun initWithStartDate_duration(startDate: MemorySegment, duration: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartDate:duration:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startDate, duration) as MemorySegment
    }
    
    open fun initWithStartDate_endDate(startDate: MemorySegment, endDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartDate:endDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startDate, endDate) as MemorySegment
    }
    
    open fun compare(dateInterval: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateInterval) as NSComparisonResult
    }
    
    open fun isEqualToDateInterval(dateInterval: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dateInterval) as BOOL
    }
    
    open fun intersectsDateInterval(dateInterval: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dateInterval) as BOOL
    }
    
    open fun intersectionWithDateInterval(dateInterval: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("intersectionWithDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateInterval) as MemorySegment
    }
    
    open fun containsDate(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    // @property startDate
    open fun startDate(): MemorySegment {
        val sel = ObjCRuntime.sel("startDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property endDate
    open fun endDate(): MemorySegment {
        val sel = ObjCRuntime.sel("endDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property duration
    open fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
}

