package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRulerView
 * Superclass: NSView
 */
open class NSRulerView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRulerView") }
        
        fun registerUnitWithName_abbreviation_unitToPointsConversionFactor_stepUpCycle_stepDownCycle(unitName: NSRulerViewUnitName, abbreviation: MemorySegment, conversionFactor: CGFloat, stepUpCycle: MemorySegment, stepDownCycle: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("registerUnitWithName:abbreviation:unitToPointsConversionFactor:stepUpCycle:stepDownCycle:")
            ObjCRuntime.msgSend(null, _class, sel, unitName, abbreviation, conversionFactor, stepUpCycle, stepDownCycle)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun registerUnitWithName_abbreviation_unitToPointsConversionFactor_stepUpCycle_stepDownCycle(unitName: NSRulerViewUnitName, abbreviation: String, conversionFactor: CGFloat, stepUpCycle: MemorySegment, stepDownCycle: MemorySegment): Unit = registerUnitWithName_abbreviation_unitToPointsConversionFactor_stepUpCycle_stepDownCycle(unitName, ObjCRuntime.newNSString(Arena.global(), abbreviation), conversionFactor, stepUpCycle, stepDownCycle)
        
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initWithScrollView_orientation(scrollView: MemorySegment, orientation: NSRulerOrientation): MemorySegment {
        val sel = ObjCRuntime.sel("initWithScrollView:orientation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, scrollView, orientation) as MemorySegment
    }
    
    fun addMarker(marker: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, marker)
    }
    
    fun removeMarker(marker: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, marker)
    }
    
    fun trackMarker_withMouseEvent(marker: MemorySegment, event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("trackMarker:withMouseEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, marker, event) as BOOL
    }
    
    fun moveRulerlineFromLocation_toLocation(oldLocation: CGFloat, newLocation: CGFloat): Unit {
        val sel = ObjCRuntime.sel("moveRulerlineFromLocation:toLocation:")
        ObjCRuntime.msgSend(null, ptr, sel, oldLocation, newLocation)
    }
    
    fun invalidateHashMarks(): Unit {
        val sel = ObjCRuntime.sel("invalidateHashMarks")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun drawHashMarksAndLabelsInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawHashMarksAndLabelsInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawMarkersInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawMarkersInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property scrollView
    fun scrollView(): MemorySegment {
        val sel = ObjCRuntime.sel("scrollView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScrollView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScrollView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property orientation
    fun orientation(): NSRulerOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRulerOrientation
    }
    fun setOrientation(value: NSRulerOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property baselineLocation
    fun baselineLocation(): CGFloat {
        val sel = ObjCRuntime.sel("baselineLocation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property requiredThickness
    fun requiredThickness(): CGFloat {
        val sel = ObjCRuntime.sel("requiredThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property ruleThickness
    fun ruleThickness(): CGFloat {
        val sel = ObjCRuntime.sel("ruleThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRuleThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRuleThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property reservedThicknessForMarkers
    fun reservedThicknessForMarkers(): CGFloat {
        val sel = ObjCRuntime.sel("reservedThicknessForMarkers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setReservedThicknessForMarkers(value: CGFloat) {
        val sel = ObjCRuntime.sel("setReservedThicknessForMarkers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property reservedThicknessForAccessoryView
    fun reservedThicknessForAccessoryView(): CGFloat {
        val sel = ObjCRuntime.sel("reservedThicknessForAccessoryView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setReservedThicknessForAccessoryView(value: CGFloat) {
        val sel = ObjCRuntime.sel("setReservedThicknessForAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property measurementUnits
    fun measurementUnits(): NSRulerViewUnitName {
        val sel = ObjCRuntime.sel("measurementUnits")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRulerViewUnitName
    }
    fun setMeasurementUnits(value: NSRulerViewUnitName) {
        val sel = ObjCRuntime.sel("setMeasurementUnits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property originOffset
    fun originOffset(): CGFloat {
        val sel = ObjCRuntime.sel("originOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setOriginOffset(value: CGFloat) {
        val sel = ObjCRuntime.sel("setOriginOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property clientView
    fun clientView(): MemorySegment {
        val sel = ObjCRuntime.sel("clientView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setClientView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setClientView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property markers
    /** @return NSArray<NSRulerMarker *> * */
    fun markers(): MemorySegment {
        val sel = ObjCRuntime.sel("markers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMarkers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMarkers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property accessoryView
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property flipped
    override fun `isFlipped`(): BOOL {
        val sel = ObjCRuntime.sel("isFlipped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

