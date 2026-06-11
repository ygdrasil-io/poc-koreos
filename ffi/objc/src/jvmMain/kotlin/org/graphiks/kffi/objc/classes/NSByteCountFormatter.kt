/**
 * Kotlin/JVM wrapper for Objective-C class: NSByteCountFormatter
 * Superclass: NSFormatter
 */
open class NSByteCountFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSByteCountFormatter") }
        
        fun stringFromByteCount_countStyle(byteCount: Long, countStyle: NSByteCountFormatterCountStyle): MemorySegment {
            val sel = ObjCRuntime.sel("stringFromByteCount:countStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, byteCount, countStyle) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun stringFromByteCount_countStyleAsString(byteCount: Long, countStyle: NSByteCountFormatterCountStyle): String = ObjCRuntime.toJavaString(stringFromByteCount_countStyle(byteCount, countStyle))
        
        fun stringFromMeasurement_countStyle(measurement: MemorySegment, countStyle: NSByteCountFormatterCountStyle): MemorySegment {
            val sel = ObjCRuntime.sel("stringFromMeasurement:countStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, measurement, countStyle) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun stringFromMeasurement_countStyleAsString(measurement: MemorySegment, countStyle: NSByteCountFormatterCountStyle): String = ObjCRuntime.toJavaString(stringFromMeasurement_countStyle(measurement, countStyle))
        
    }
    
    fun stringFromByteCount(byteCount: Long): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromByteCount:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, byteCount) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromByteCountAsString(byteCount: Long): String = ObjCRuntime.toJavaString(stringFromByteCount(byteCount))
    
    fun stringFromMeasurement(measurement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromMeasurement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, measurement) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromMeasurementAsString(measurement: MemorySegment): String = ObjCRuntime.toJavaString(stringFromMeasurement(measurement))
    
    fun stringForObjectValue(obj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringForObjectValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, obj) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringForObjectValueAsString(obj: MemorySegment): String = ObjCRuntime.toJavaString(stringForObjectValue(obj))
    
    // @property allowedUnits
    fun allowedUnits(): NSByteCountFormatterUnits {
        val sel = ObjCRuntime.sel("allowedUnits")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSByteCountFormatterUnits
    }
    fun setAllowedUnits(value: NSByteCountFormatterUnits) {
        val sel = ObjCRuntime.sel("setAllowedUnits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property countStyle
    fun countStyle(): NSByteCountFormatterCountStyle {
        val sel = ObjCRuntime.sel("countStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSByteCountFormatterCountStyle
    }
    fun setCountStyle(value: NSByteCountFormatterCountStyle) {
        val sel = ObjCRuntime.sel("setCountStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsNonnumericFormatting
    fun allowsNonnumericFormatting(): BOOL {
        val sel = ObjCRuntime.sel("allowsNonnumericFormatting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsNonnumericFormatting(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsNonnumericFormatting:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesUnit
    fun includesUnit(): BOOL {
        val sel = ObjCRuntime.sel("includesUnit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesUnit(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesUnit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesCount
    fun includesCount(): BOOL {
        val sel = ObjCRuntime.sel("includesCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesCount(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesActualByteCount
    fun includesActualByteCount(): BOOL {
        val sel = ObjCRuntime.sel("includesActualByteCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesActualByteCount(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesActualByteCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property adaptive
    fun isAdaptive(): BOOL {
        val sel = ObjCRuntime.sel("isAdaptive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAdaptive(value: BOOL) {
        val sel = ObjCRuntime.sel("setAdaptive:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property zeroPadsFractionDigits
    fun zeroPadsFractionDigits(): BOOL {
        val sel = ObjCRuntime.sel("zeroPadsFractionDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setZeroPadsFractionDigits(value: BOOL) {
        val sel = ObjCRuntime.sel("setZeroPadsFractionDigits:")
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
    
}

