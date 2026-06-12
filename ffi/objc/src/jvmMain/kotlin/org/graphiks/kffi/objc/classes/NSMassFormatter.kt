package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMassFormatter
 * Superclass: NSFormatter
 */
open class NSMassFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMassFormatter") }
        
    }
    
    fun stringFromValue_unit(value: Double, unit: NSMassFormatterUnit): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromValue:unit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, unit) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromValue_unitAsString(value: Double, unit: NSMassFormatterUnit): String = ObjCRuntime.toJavaString(stringFromValue_unit(value, unit))
    
    fun stringFromKilograms(numberInKilograms: Double): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromKilograms:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numberInKilograms) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromKilogramsAsString(numberInKilograms: Double): String = ObjCRuntime.toJavaString(stringFromKilograms(numberInKilograms))
    
    fun unitStringFromValue_unit(value: Double, unit: NSMassFormatterUnit): MemorySegment {
        val sel = ObjCRuntime.sel("unitStringFromValue:unit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, unit) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun unitStringFromValue_unitAsString(value: Double, unit: NSMassFormatterUnit): String = ObjCRuntime.toJavaString(unitStringFromValue_unit(value, unit))
    
    fun unitStringFromKilograms_usedUnit(numberInKilograms: Double, unitp: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("unitStringFromKilograms:usedUnit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numberInKilograms, unitp) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun unitStringFromKilograms_usedUnitAsString(numberInKilograms: Double, unitp: MemorySegment): String = ObjCRuntime.toJavaString(unitStringFromKilograms_usedUnit(numberInKilograms, unitp))
    
    override fun `getObjectValue_forString_errorDescription`(obj: MemorySegment, string: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:errorDescription:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `getObjectValue_forString_errorDescription`(obj: MemorySegment, string: String, error: String): BOOL = getObjectValue_forString_errorDescription(obj, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), error))
    
    // @property numberFormatter
    fun numberFormatter(): MemorySegment {
        val sel = ObjCRuntime.sel("numberFormatter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNumberFormatter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNumberFormatter:")
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
    
    // @property forPersonMassUse
    fun isForPersonMassUse(): BOOL {
        val sel = ObjCRuntime.sel("isForPersonMassUse")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setForPersonMassUse(value: BOOL) {
        val sel = ObjCRuntime.sel("setForPersonMassUse:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

