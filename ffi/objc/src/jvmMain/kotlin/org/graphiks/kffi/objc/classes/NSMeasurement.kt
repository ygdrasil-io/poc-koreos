/**
 * Kotlin/JVM wrapper for Objective-C class: NSMeasurement
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSMeasurement(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMeasurement") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithDoubleValue_unit(doubleValue: Double, unit: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDoubleValue:unit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, doubleValue, unit) as MemorySegment
    }
    
    fun canBeConvertedToUnit(unit: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canBeConvertedToUnit:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, unit) as BOOL
    }
    
    fun measurementByConvertingToUnit(unit: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("measurementByConvertingToUnit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unit) as MemorySegment
    }
    
    /** @return NSMeasurement<UnitType> * */
    fun measurementByAddingMeasurement(measurement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("measurementByAddingMeasurement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, measurement) as MemorySegment
    }
    
    /** @return NSMeasurement<UnitType> * */
    fun measurementBySubtractingMeasurement(measurement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("measurementBySubtractingMeasurement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, measurement) as MemorySegment
    }
    
    // @property unit
    fun unit(): MemorySegment {
        val sel = ObjCRuntime.sel("unit")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _unit: MemorySegment
    // ivar: _doubleValue: Double
}

