/**
 * Kotlin/JVM wrapper for Objective-C class: NSDatePicker
 * Superclass: NSControl
 */
open class NSDatePicker(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDatePicker") }
        
    }
    
    // @property datePickerStyle
    fun datePickerStyle(): NSDatePickerStyle {
        val sel = ObjCRuntime.sel("datePickerStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDatePickerStyle
    }
    fun setDatePickerStyle(value: NSDatePickerStyle) {
        val sel = ObjCRuntime.sel("setDatePickerStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezeled
    fun isBezeled(): BOOL {
        val sel = ObjCRuntime.sel("isBezeled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBezeled(value: BOOL) {
        val sel = ObjCRuntime.sel("setBezeled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bordered
    fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsBackground
    fun drawsBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textColor
    fun textColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property datePickerMode
    fun datePickerMode(): NSDatePickerMode {
        val sel = ObjCRuntime.sel("datePickerMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDatePickerMode
    }
    fun setDatePickerMode(value: NSDatePickerMode) {
        val sel = ObjCRuntime.sel("setDatePickerMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property datePickerElements
    fun datePickerElements(): NSDatePickerElementFlags {
        val sel = ObjCRuntime.sel("datePickerElements")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDatePickerElementFlags
    }
    fun setDatePickerElements(value: NSDatePickerElementFlags) {
        val sel = ObjCRuntime.sel("setDatePickerElements:")
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
    
    // @property timeZone
    fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dateValue
    fun dateValue(): MemorySegment {
        val sel = ObjCRuntime.sel("dateValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDateValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDateValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeInterval
    fun timeInterval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setTimeInterval(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTimeInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minDate
    fun minDate(): MemorySegment {
        val sel = ObjCRuntime.sel("minDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMinDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMinDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxDate
    fun maxDate(): MemorySegment {
        val sel = ObjCRuntime.sel("maxDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMaxDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMaxDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property presentsCalendarOverlay
    fun presentsCalendarOverlay(): BOOL {
        val sel = ObjCRuntime.sel("presentsCalendarOverlay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPresentsCalendarOverlay(value: BOOL) {
        val sel = ObjCRuntime.sel("setPresentsCalendarOverlay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSDatePickerCellDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

