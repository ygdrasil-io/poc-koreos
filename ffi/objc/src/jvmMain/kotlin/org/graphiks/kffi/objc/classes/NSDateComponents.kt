/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateComponents
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSDateComponents(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateComponents") }
        
    }
    
    fun week(): NSInteger {
        val sel = ObjCRuntime.sel("week")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun setWeek(v: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setWeek:")
        ObjCRuntime.msgSend(null, ptr, sel, v)
    }
    
    fun setValue_forComponent(value: NSInteger, unit: NSCalendarUnit): Unit {
        val sel = ObjCRuntime.sel("setValue:forComponent:")
        ObjCRuntime.msgSend(null, ptr, sel, value, unit)
    }
    
    fun valueForComponent(unit: NSCalendarUnit): NSInteger {
        val sel = ObjCRuntime.sel("valueForComponent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, unit) as NSInteger
    }
    
    fun isValidDateInCalendar(calendar: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isValidDateInCalendar:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, calendar) as BOOL
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
    
    // @property era
    fun era(): NSInteger {
        val sel = ObjCRuntime.sel("era")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setEra(value: NSInteger) {
        val sel = ObjCRuntime.sel("setEra:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property year
    fun year(): NSInteger {
        val sel = ObjCRuntime.sel("year")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setYear(value: NSInteger) {
        val sel = ObjCRuntime.sel("setYear:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property month
    fun month(): NSInteger {
        val sel = ObjCRuntime.sel("month")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMonth(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMonth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property day
    fun day(): NSInteger {
        val sel = ObjCRuntime.sel("day")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setDay(value: NSInteger) {
        val sel = ObjCRuntime.sel("setDay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hour
    fun hour(): NSInteger {
        val sel = ObjCRuntime.sel("hour")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setHour(value: NSInteger) {
        val sel = ObjCRuntime.sel("setHour:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minute
    fun minute(): NSInteger {
        val sel = ObjCRuntime.sel("minute")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMinute(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMinute:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property second
    fun second(): NSInteger {
        val sel = ObjCRuntime.sel("second")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSecond(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSecond:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property nanosecond
    fun nanosecond(): NSInteger {
        val sel = ObjCRuntime.sel("nanosecond")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNanosecond(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNanosecond:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property weekday
    fun weekday(): NSInteger {
        val sel = ObjCRuntime.sel("weekday")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setWeekday(value: NSInteger) {
        val sel = ObjCRuntime.sel("setWeekday:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property weekdayOrdinal
    fun weekdayOrdinal(): NSInteger {
        val sel = ObjCRuntime.sel("weekdayOrdinal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setWeekdayOrdinal(value: NSInteger) {
        val sel = ObjCRuntime.sel("setWeekdayOrdinal:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property quarter
    fun quarter(): NSInteger {
        val sel = ObjCRuntime.sel("quarter")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setQuarter(value: NSInteger) {
        val sel = ObjCRuntime.sel("setQuarter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property weekOfMonth
    fun weekOfMonth(): NSInteger {
        val sel = ObjCRuntime.sel("weekOfMonth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setWeekOfMonth(value: NSInteger) {
        val sel = ObjCRuntime.sel("setWeekOfMonth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property weekOfYear
    fun weekOfYear(): NSInteger {
        val sel = ObjCRuntime.sel("weekOfYear")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setWeekOfYear(value: NSInteger) {
        val sel = ObjCRuntime.sel("setWeekOfYear:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property yearForWeekOfYear
    fun yearForWeekOfYear(): NSInteger {
        val sel = ObjCRuntime.sel("yearForWeekOfYear")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setYearForWeekOfYear(value: NSInteger) {
        val sel = ObjCRuntime.sel("setYearForWeekOfYear:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dayOfYear
    fun dayOfYear(): NSInteger {
        val sel = ObjCRuntime.sel("dayOfYear")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setDayOfYear(value: NSInteger) {
        val sel = ObjCRuntime.sel("setDayOfYear:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property leapMonth
    fun isLeapMonth(): BOOL {
        val sel = ObjCRuntime.sel("isLeapMonth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLeapMonth(value: BOOL) {
        val sel = ObjCRuntime.sel("setLeapMonth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property repeatedDay
    fun isRepeatedDay(): BOOL {
        val sel = ObjCRuntime.sel("isRepeatedDay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRepeatedDay(value: BOOL) {
        val sel = ObjCRuntime.sel("setRepeatedDay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property date
    fun date(): MemorySegment {
        val sel = ObjCRuntime.sel("date")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property validDate
    fun isValidDate(): BOOL {
        val sel = ObjCRuntime.sel("isValidDate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

