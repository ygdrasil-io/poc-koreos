package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSLevelIndicator
 * Superclass: NSControl
 */
open class NSLevelIndicator(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLevelIndicator") }
        
    }
    
    fun tickMarkValueAtIndex(index: NSInteger): Double {
        val sel = ObjCRuntime.sel("tickMarkValueAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, index) as Double
    }
    
    fun rectOfTickMarkAtIndex(index: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("rectOfTickMarkAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index) as NSRect
    }
    
    // @property levelIndicatorStyle
    fun levelIndicatorStyle(): NSLevelIndicatorStyle {
        val sel = ObjCRuntime.sel("levelIndicatorStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLevelIndicatorStyle
    }
    fun setLevelIndicatorStyle(value: NSLevelIndicatorStyle) {
        val sel = ObjCRuntime.sel("setLevelIndicatorStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minValue
    fun minValue(): Double {
        val sel = ObjCRuntime.sel("minValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMinValue(value: Double) {
        val sel = ObjCRuntime.sel("setMinValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxValue
    fun maxValue(): Double {
        val sel = ObjCRuntime.sel("maxValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMaxValue(value: Double) {
        val sel = ObjCRuntime.sel("setMaxValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property warningValue
    fun warningValue(): Double {
        val sel = ObjCRuntime.sel("warningValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setWarningValue(value: Double) {
        val sel = ObjCRuntime.sel("setWarningValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property criticalValue
    fun criticalValue(): Double {
        val sel = ObjCRuntime.sel("criticalValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setCriticalValue(value: Double) {
        val sel = ObjCRuntime.sel("setCriticalValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tickMarkPosition
    fun tickMarkPosition(): NSTickMarkPosition {
        val sel = ObjCRuntime.sel("tickMarkPosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
    }
    fun setTickMarkPosition(value: NSTickMarkPosition) {
        val sel = ObjCRuntime.sel("setTickMarkPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfTickMarks
    fun numberOfTickMarks(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfTickMarks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfTickMarks(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfMajorTickMarks
    fun numberOfMajorTickMarks(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfMajorTickMarks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfMajorTickMarks(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfMajorTickMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fillColor
    fun fillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("fillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFillColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFillColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property warningFillColor
    fun warningFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("warningFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setWarningFillColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setWarningFillColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property criticalFillColor
    fun criticalFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("criticalFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCriticalFillColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCriticalFillColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsTieredCapacityLevels
    fun drawsTieredCapacityLevels(): BOOL {
        val sel = ObjCRuntime.sel("drawsTieredCapacityLevels")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsTieredCapacityLevels(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsTieredCapacityLevels:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property placeholderVisibility
    fun placeholderVisibility(): NSLevelIndicatorPlaceholderVisibility {
        val sel = ObjCRuntime.sel("placeholderVisibility")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLevelIndicatorPlaceholderVisibility
    }
    fun setPlaceholderVisibility(value: NSLevelIndicatorPlaceholderVisibility) {
        val sel = ObjCRuntime.sel("setPlaceholderVisibility:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property ratingImage
    fun ratingImage(): MemorySegment {
        val sel = ObjCRuntime.sel("ratingImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRatingImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRatingImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property ratingPlaceholderImage
    fun ratingPlaceholderImage(): MemorySegment {
        val sel = ObjCRuntime.sel("ratingPlaceholderImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRatingPlaceholderImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRatingPlaceholderImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

