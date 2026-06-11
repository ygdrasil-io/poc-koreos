/**
 * Kotlin/JVM wrapper for Objective-C class: NSMeasurementFormatter
 * Superclass: NSFormatter
 * Protocols: NSSecureCoding
 */
open class NSMeasurementFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMeasurementFormatter") }
        
    }
    
    fun stringFromMeasurement(measurement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromMeasurement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, measurement) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromMeasurementAsString(measurement: MemorySegment): String = ObjCRuntime.toJavaString(stringFromMeasurement(measurement))
    
    fun stringFromUnit(unit: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromUnit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unit) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromUnitAsString(unit: MemorySegment): String = ObjCRuntime.toJavaString(stringFromUnit(unit))
    
    // @property unitOptions
    fun unitOptions(): NSMeasurementFormatterUnitOptions {
        val sel = ObjCRuntime.sel("unitOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMeasurementFormatterUnitOptions
    }
    fun setUnitOptions(value: NSMeasurementFormatterUnitOptions) {
        val sel = ObjCRuntime.sel("setUnitOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property unitStyle
    fun unitStyle(): NSFormattingUnitStyle {
        val sel = ObjCRuntime.sel("unitStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFormattingUnitStyle
    }
    fun setUnitStyle(value: NSFormattingUnitStyle) {
        val sel = ObjCRuntime.sel("setUnitStyle:")
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
    
    // @property numberFormatter
    fun numberFormatter(): MemorySegment {
        val sel = ObjCRuntime.sel("numberFormatter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNumberFormatter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNumberFormatter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _formatter: MemorySegment
}

