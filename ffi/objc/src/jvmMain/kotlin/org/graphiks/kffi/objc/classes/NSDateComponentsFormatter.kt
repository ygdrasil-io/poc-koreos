package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateComponentsFormatter
 * Superclass: NSFormatter
 */
open class NSDateComponentsFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateComponentsFormatter") }
        
        fun localizedStringFromDateComponents_unitsStyle(components: MemorySegment, unitsStyle: NSDateComponentsFormatterUnitsStyle): MemorySegment {
            val sel = ObjCRuntime.sel("localizedStringFromDateComponents:unitsStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, components, unitsStyle) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedStringFromDateComponents_unitsStyleAsString(components: MemorySegment, unitsStyle: NSDateComponentsFormatterUnitsStyle): String = ObjCRuntime.toJavaString(localizedStringFromDateComponents_unitsStyle(components, unitsStyle))
        
    }
    
    override fun `stringForObjectValue`(obj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringForObjectValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, obj) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    override fun `stringForObjectValueAsString`(obj: MemorySegment): String = ObjCRuntime.toJavaString(stringForObjectValue(obj))
    
    fun stringFromDateComponents(components: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDateComponents:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, components) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDateComponentsAsString(components: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDateComponents(components))
    
    fun stringFromDate_toDate(startDate: MemorySegment, endDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDate:toDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startDate, endDate) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDate_toDateAsString(startDate: MemorySegment, endDate: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDate_toDate(startDate, endDate))
    
    fun stringFromTimeInterval(ti: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromTimeInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ti) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromTimeIntervalAsString(ti: NSTimeInterval): String = ObjCRuntime.toJavaString(stringFromTimeInterval(ti))
    
    override fun `getObjectValue_forString_errorDescription`(obj: MemorySegment, string: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:errorDescription:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun getObjectValue_forString_errorDescription(obj: MemorySegment, string: String, error: String): BOOL = getObjectValue_forString_errorDescription(obj, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), error))
    
    // @property unitsStyle
    fun unitsStyle(): NSDateComponentsFormatterUnitsStyle {
        val sel = ObjCRuntime.sel("unitsStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateComponentsFormatterUnitsStyle
    }
    fun setUnitsStyle(value: NSDateComponentsFormatterUnitsStyle) {
        val sel = ObjCRuntime.sel("setUnitsStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedUnits
    fun allowedUnits(): NSCalendarUnit {
        val sel = ObjCRuntime.sel("allowedUnits")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCalendarUnit
    }
    fun setAllowedUnits(value: NSCalendarUnit) {
        val sel = ObjCRuntime.sel("setAllowedUnits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property zeroFormattingBehavior
    fun zeroFormattingBehavior(): NSDateComponentsFormatterZeroFormattingBehavior {
        val sel = ObjCRuntime.sel("zeroFormattingBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateComponentsFormatterZeroFormattingBehavior
    }
    fun setZeroFormattingBehavior(value: NSDateComponentsFormatterZeroFormattingBehavior) {
        val sel = ObjCRuntime.sel("setZeroFormattingBehavior:")
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
    
    // @property referenceDate
    fun referenceDate(): MemorySegment {
        val sel = ObjCRuntime.sel("referenceDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setReferenceDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setReferenceDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsFractionalUnits
    fun allowsFractionalUnits(): BOOL {
        val sel = ObjCRuntime.sel("allowsFractionalUnits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsFractionalUnits(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsFractionalUnits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumUnitCount
    fun maximumUnitCount(): NSInteger {
        val sel = ObjCRuntime.sel("maximumUnitCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaximumUnitCount(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaximumUnitCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsesLargestUnit
    fun collapsesLargestUnit(): BOOL {
        val sel = ObjCRuntime.sel("collapsesLargestUnit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCollapsesLargestUnit(value: BOOL) {
        val sel = ObjCRuntime.sel("setCollapsesLargestUnit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesApproximationPhrase
    fun includesApproximationPhrase(): BOOL {
        val sel = ObjCRuntime.sel("includesApproximationPhrase")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesApproximationPhrase(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesApproximationPhrase:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesTimeRemainingPhrase
    fun includesTimeRemainingPhrase(): BOOL {
        val sel = ObjCRuntime.sel("includesTimeRemainingPhrase")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesTimeRemainingPhrase(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesTimeRemainingPhrase:")
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

