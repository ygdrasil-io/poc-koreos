/**
 * Kotlin/JVM wrapper for Objective-C class: NSRelativeDateTimeFormatter
 * Superclass: NSFormatter
 */
open class NSRelativeDateTimeFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRelativeDateTimeFormatter") }
        
    }
    
    fun localizedStringFromDateComponents(dateComponents: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedStringFromDateComponents:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dateComponents) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedStringFromDateComponentsAsString(dateComponents: MemorySegment): String = ObjCRuntime.toJavaString(localizedStringFromDateComponents(dateComponents))
    
    fun localizedStringFromTimeInterval(timeInterval: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("localizedStringFromTimeInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, timeInterval) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedStringFromTimeIntervalAsString(timeInterval: NSTimeInterval): String = ObjCRuntime.toJavaString(localizedStringFromTimeInterval(timeInterval))
    
    fun localizedStringForDate_relativeToDate(date: MemorySegment, referenceDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedStringForDate:relativeToDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, referenceDate) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedStringForDate_relativeToDateAsString(date: MemorySegment, referenceDate: MemorySegment): String = ObjCRuntime.toJavaString(localizedStringForDate_relativeToDate(date, referenceDate))
    
    fun stringForObjectValue(obj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringForObjectValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, obj) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringForObjectValueAsString(obj: MemorySegment): String = ObjCRuntime.toJavaString(stringForObjectValue(obj))
    
    // @property dateTimeStyle
    fun dateTimeStyle(): NSRelativeDateTimeFormatterStyle {
        val sel = ObjCRuntime.sel("dateTimeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRelativeDateTimeFormatterStyle
    }
    fun setDateTimeStyle(value: NSRelativeDateTimeFormatterStyle) {
        val sel = ObjCRuntime.sel("setDateTimeStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property unitsStyle
    fun unitsStyle(): NSRelativeDateTimeFormatterUnitsStyle {
        val sel = ObjCRuntime.sel("unitsStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRelativeDateTimeFormatterUnitsStyle
    }
    fun setUnitsStyle(value: NSRelativeDateTimeFormatterUnitsStyle) {
        val sel = ObjCRuntime.sel("setUnitsStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formattingContext
    fun formattingContext(): NSFormattingContext {
        val sel = ObjCRuntime.sel("formattingContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFormattingContext
    }
    fun setFormattingContext(value: NSFormattingContext) {
        val sel = ObjCRuntime.sel("setFormattingContext:")
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
    
    // @property locale
    fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

