package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSlider
 * Superclass: NSControl
 * Protocols: NSAccessibilitySlider
 */
open class NSSlider(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSlider") }
        
    }
    
    fun acceptsFirstMouse(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("acceptsFirstMouse:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    // @property sliderType
    fun sliderType(): NSSliderType {
        val sel = ObjCRuntime.sel("sliderType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSliderType
    }
    fun setSliderType(value: NSSliderType) {
        val sel = ObjCRuntime.sel("setSliderType:")
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
    
    // @property neutralValue
    fun neutralValue(): Double {
        val sel = ObjCRuntime.sel("neutralValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setNeutralValue(value: Double) {
        val sel = ObjCRuntime.sel("setNeutralValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property altIncrementValue
    fun altIncrementValue(): Double {
        val sel = ObjCRuntime.sel("altIncrementValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setAltIncrementValue(value: Double) {
        val sel = ObjCRuntime.sel("setAltIncrementValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property knobThickness
    fun knobThickness(): CGFloat {
        val sel = ObjCRuntime.sel("knobThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property vertical
    fun isVertical(): BOOL {
        val sel = ObjCRuntime.sel("isVertical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVertical(value: BOOL) {
        val sel = ObjCRuntime.sel("setVertical:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property trackFillColor
    fun trackFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("trackFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTrackFillColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTrackFillColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tintProminence
    fun tintProminence(): NSTintProminence {
        val sel = ObjCRuntime.sel("tintProminence")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTintProminence
    }
    fun setTintProminence(value: NSTintProminence) {
        val sel = ObjCRuntime.sel("setTintProminence:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSSliderVerticalGetter on NSSlider ─────────────────────────────────────────

fun NSSlider.isVertical(): BOOL {
    val sel = ObjCRuntime.sel("isVertical")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property vertical
fun NSSlider.isVertical(): BOOL {
    val sel = ObjCRuntime.sel("isVertical")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSTickMarkSupport on NSSlider ─────────────────────────────────────────

fun NSSlider.tickMarkValueAtIndex(index: NSInteger): Double {
    val sel = ObjCRuntime.sel("tickMarkValueAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, index) as Double
}

fun NSSlider.rectOfTickMarkAtIndex(index: NSInteger): NSRect {
    val sel = ObjCRuntime.sel("rectOfTickMarkAtIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index) as NSRect
}

fun NSSlider.indexOfTickMarkAtPoint(point: NSPoint): NSInteger {
    val sel = ObjCRuntime.sel("indexOfTickMarkAtPoint:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, point) as NSInteger
}

fun NSSlider.closestTickMarkValueToValue(value: Double): Double {
    val sel = ObjCRuntime.sel("closestTickMarkValueToValue:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, value) as Double
}

fun NSSlider.numberOfTickMarks(): NSInteger {
    val sel = ObjCRuntime.sel("numberOfTickMarks")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSSlider.setNumberOfTickMarks(numberOfTickMarks: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
    ObjCRuntime.msgSend(null, ptr, sel, numberOfTickMarks)
}

fun NSSlider.tickMarkPosition(): NSTickMarkPosition {
    val sel = ObjCRuntime.sel("tickMarkPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
}

fun NSSlider.setTickMarkPosition(tickMarkPosition: NSTickMarkPosition): Unit {
    val sel = ObjCRuntime.sel("setTickMarkPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, tickMarkPosition)
}

fun NSSlider.allowsTickMarkValuesOnly(): BOOL {
    val sel = ObjCRuntime.sel("allowsTickMarkValuesOnly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSSlider.setAllowsTickMarkValuesOnly(allowsTickMarkValuesOnly: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsTickMarkValuesOnly:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsTickMarkValuesOnly)
}

// @property numberOfTickMarks
fun NSSlider.numberOfTickMarks(): NSInteger {
    val sel = ObjCRuntime.sel("numberOfTickMarks")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}
fun NSSlider.setNumberOfTickMarks(value: NSInteger) {
    val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property tickMarkPosition
fun NSSlider.tickMarkPosition(): NSTickMarkPosition {
    val sel = ObjCRuntime.sel("tickMarkPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
}
fun NSSlider.setTickMarkPosition(value: NSTickMarkPosition) {
    val sel = ObjCRuntime.sel("setTickMarkPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property allowsTickMarkValuesOnly
fun NSSlider.allowsTickMarkValuesOnly(): BOOL {
    val sel = ObjCRuntime.sel("allowsTickMarkValuesOnly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSSlider.setAllowsTickMarkValuesOnly(value: BOOL) {
    val sel = ObjCRuntime.sel("setAllowsTickMarkValuesOnly:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSSliderConvenience on NSSlider ─────────────────────────────────────────

// Class<*> method: +[NSSlider sliderWithTarget:action:]
fun NSSlider_sliderWithTarget_action(target: MemorySegment, action: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sliderWithTarget:action:")
    val cls = ObjCRuntime.getClass("NSSlider")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, target, action) as MemorySegment
}

// Class<*> method: +[NSSlider sliderWithValue:minValue:maxValue:target:action:]
fun NSSlider_sliderWithValue_minValue_maxValue_target_action(value: Double, minValue: Double, maxValue: Double, target: MemorySegment, action: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sliderWithValue:minValue:maxValue:target:action:")
    val cls = ObjCRuntime.getClass("NSSlider")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value, minValue, maxValue, target, action) as MemorySegment
}

// ── Category: NSSliderDeprecated on NSSlider ─────────────────────────────────────────

fun NSSlider.setTitleCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSSlider.titleCell(): MemorySegment {
    val sel = ObjCRuntime.sel("titleCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSlider.setTitleColor(newColor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleColor:")
    ObjCRuntime.msgSend(null, ptr, sel, newColor)
}

fun NSSlider.titleColor(): MemorySegment {
    val sel = ObjCRuntime.sel("titleColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSlider.setTitleFont(fontObj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleFont:")
    ObjCRuntime.msgSend(null, ptr, sel, fontObj)
}

fun NSSlider.titleFont(): MemorySegment {
    val sel = ObjCRuntime.sel("titleFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSlider.title(): MemorySegment {
    val sel = ObjCRuntime.sel("title")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSlider.setTitle(string: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, string)
}

fun NSSlider.setKnobThickness(thickness: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setKnobThickness:")
    ObjCRuntime.msgSend(null, ptr, sel, thickness)
}

fun NSSlider.setImage(backgroundImage: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setImage:")
    ObjCRuntime.msgSend(null, ptr, sel, backgroundImage)
}

fun NSSlider.image(): MemorySegment {
    val sel = ObjCRuntime.sel("image")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

