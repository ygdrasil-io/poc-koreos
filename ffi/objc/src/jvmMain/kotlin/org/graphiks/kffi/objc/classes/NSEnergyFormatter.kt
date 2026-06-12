package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSEnergyFormatter
 * Superclass: NSFormatter
 */
open class NSEnergyFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSEnergyFormatter") }
        
    }
    
    fun stringFromValue_unit(value: Double, unit: NSEnergyFormatterUnit): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromValue:unit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, unit) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromValue_unitAsString(value: Double, unit: NSEnergyFormatterUnit): String = ObjCRuntime.toJavaString(stringFromValue_unit(value, unit))
    
    fun stringFromJoules(numberInJoules: Double): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromJoules:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numberInJoules) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromJoulesAsString(numberInJoules: Double): String = ObjCRuntime.toJavaString(stringFromJoules(numberInJoules))
    
    fun unitStringFromValue_unit(value: Double, unit: NSEnergyFormatterUnit): MemorySegment {
        val sel = ObjCRuntime.sel("unitStringFromValue:unit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, unit) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun unitStringFromValue_unitAsString(value: Double, unit: NSEnergyFormatterUnit): String = ObjCRuntime.toJavaString(unitStringFromValue_unit(value, unit))
    
    fun unitStringFromJoules_usedUnit(numberInJoules: Double, unitp: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("unitStringFromJoules:usedUnit:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numberInJoules, unitp) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun unitStringFromJoules_usedUnitAsString(numberInJoules: Double, unitp: MemorySegment): String = ObjCRuntime.toJavaString(unitStringFromJoules_usedUnit(numberInJoules, unitp))
    
    override fun `getObjectValue_forString_errorDescription`(obj: MemorySegment, string: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:errorDescription:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun getObjectValue_forString_errorDescription(obj: MemorySegment, string: String, error: String): BOOL = getObjectValue_forString_errorDescription(obj, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), error))
    
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
    
    // @property forFoodEnergyUse
    fun isForFoodEnergyUse(): BOOL {
        val sel = ObjCRuntime.sel("isForFoodEnergyUse")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setForFoodEnergyUse(value: BOOL) {
        val sel = ObjCRuntime.sel("setForFoodEnergyUse:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

