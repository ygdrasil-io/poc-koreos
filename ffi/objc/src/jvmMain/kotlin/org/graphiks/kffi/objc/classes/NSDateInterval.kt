/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateInterval
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSDateInterval(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateInterval") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initWithStartDate_duration(startDate: MemorySegment, duration: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartDate:duration:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startDate, duration) as MemorySegment
    }
    
    fun initWithStartDate_endDate(startDate: MemorySegment, endDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartDate:endDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startDate, endDate) as MemorySegment
    }
    
    fun compare(dateInterval: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateInterval) as NSComparisonResult
    }
    
    fun isEqualToDateInterval(dateInterval: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dateInterval) as BOOL
    }
    
    fun intersectsDateInterval(dateInterval: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dateInterval) as BOOL
    }
    
    fun intersectionWithDateInterval(dateInterval: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("intersectionWithDateInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateInterval) as MemorySegment
    }
    
    fun containsDate(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    // @property startDate
    fun startDate(): MemorySegment {
        val sel = ObjCRuntime.sel("startDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property endDate
    fun endDate(): MemorySegment {
        val sel = ObjCRuntime.sel("endDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property duration
    fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
}

