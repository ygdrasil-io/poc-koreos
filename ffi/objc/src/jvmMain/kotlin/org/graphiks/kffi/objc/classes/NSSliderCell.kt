package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSliderCell
 * Superclass: NSActionCell
 */
open class NSSliderCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSliderCell") }
        
        fun prefersTrackingUntilMouseUp(): BOOL {
            val sel = ObjCRuntime.sel("prefersTrackingUntilMouseUp")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun knobRectFlipped(flipped: BOOL): NSRect {
        val sel = ObjCRuntime.sel("knobRectFlipped:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, flipped) as NSRect
    }
    
    fun barRectFlipped(flipped: BOOL): NSRect {
        val sel = ObjCRuntime.sel("barRectFlipped:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, flipped) as NSRect
    }
    
    fun drawKnob(knobRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawKnob:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(knobRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawBarInside_flipped(rect: NSRect, flipped: BOOL): Unit {
        val sel = ObjCRuntime.sel("drawBarInside:flipped:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), flipped)
    }
    
    // @property prefersTrackingUntilMouseUp
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
    
    // @property altIncrementValue
    fun altIncrementValue(): Double {
        val sel = ObjCRuntime.sel("altIncrementValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setAltIncrementValue(value: Double) {
        val sel = ObjCRuntime.sel("setAltIncrementValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property vertical
    fun isVertical(): BOOL {
        val sel = ObjCRuntime.sel("isVertical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVertical(value: BOOL) {
        val sel = ObjCRuntime.sel("setVertical:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property trackRect
    fun trackRect(): NSRect {
        val sel = ObjCRuntime.sel("trackRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property knobThickness
    fun knobThickness(): CGFloat {
        val sel = ObjCRuntime.sel("knobThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

// ── Category: NSSliderCellVerticalGetter on NSSliderCell ─────────────────────────────────────────

fun NSSliderCell.isVertical(): BOOL {
    val sel = ObjCRuntime.sel("isVertical")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property vertical
    val sel = ObjCRuntime.sel("isVertical")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSTickMarkSupport on NSSliderCell ─────────────────────────────────────────

fun NSSliderCell.tickMarkValueAtIndex(index: NSInteger): Double {
    val sel = ObjCRuntime.sel("tickMarkValueAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, index) as Double
}

fun NSSliderCell.rectOfTickMarkAtIndex(index: NSInteger): NSRect {
    val sel = ObjCRuntime.sel("rectOfTickMarkAtIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index) as NSRect
}

fun NSSliderCell.indexOfTickMarkAtPoint(point: NSPoint): NSInteger {
    val sel = ObjCRuntime.sel("indexOfTickMarkAtPoint:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, point) as NSInteger
}

fun NSSliderCell.closestTickMarkValueToValue(value: Double): Double {
    val sel = ObjCRuntime.sel("closestTickMarkValueToValue:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, value) as Double
}

fun NSSliderCell.drawTickMarks(): Unit {
    val sel = ObjCRuntime.sel("drawTickMarks")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSSliderCell.numberOfTickMarks(): NSInteger {
    val sel = ObjCRuntime.sel("numberOfTickMarks")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSSliderCell.setNumberOfTickMarks(numberOfTickMarks: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
    ObjCRuntime.msgSend(null, ptr, sel, numberOfTickMarks)
}

fun NSSliderCell.tickMarkPosition(): NSTickMarkPosition {
    val sel = ObjCRuntime.sel("tickMarkPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
}

fun NSSliderCell.setTickMarkPosition(tickMarkPosition: NSTickMarkPosition): Unit {
    val sel = ObjCRuntime.sel("setTickMarkPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, tickMarkPosition)
}

fun NSSliderCell.allowsTickMarkValuesOnly(): BOOL {
    val sel = ObjCRuntime.sel("allowsTickMarkValuesOnly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSSliderCell.setAllowsTickMarkValuesOnly(allowsTickMarkValuesOnly: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsTickMarkValuesOnly:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsTickMarkValuesOnly)
}

// @property numberOfTickMarks
    val sel = ObjCRuntime.sel("numberOfTickMarks")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}
    val sel = ObjCRuntime.sel("setNumberOfTickMarks:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property tickMarkPosition
    val sel = ObjCRuntime.sel("tickMarkPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTickMarkPosition
}
    val sel = ObjCRuntime.sel("setTickMarkPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property allowsTickMarkValuesOnly
    val sel = ObjCRuntime.sel("allowsTickMarkValuesOnly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setAllowsTickMarkValuesOnly:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSDeprecated on NSSliderCell ─────────────────────────────────────────

fun NSSliderCell.setTitleCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSSliderCell.titleCell(): MemorySegment {
    val sel = ObjCRuntime.sel("titleCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSliderCell.setTitleColor(newColor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleColor:")
    ObjCRuntime.msgSend(null, ptr, sel, newColor)
}

fun NSSliderCell.titleColor(): MemorySegment {
    val sel = ObjCRuntime.sel("titleColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSliderCell.setTitleFont(fontObj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleFont:")
    ObjCRuntime.msgSend(null, ptr, sel, fontObj)
}

fun NSSliderCell.titleFont(): MemorySegment {
    val sel = ObjCRuntime.sel("titleFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSliderCell.title(): MemorySegment {
    val sel = ObjCRuntime.sel("title")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSliderCell.setTitle(string: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, string)
}

fun NSSliderCell.setKnobThickness(thickness: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setKnobThickness:")
    ObjCRuntime.msgSend(null, ptr, sel, thickness)
}

fun NSSliderCell.setImage(backgroundImage: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setImage:")
    ObjCRuntime.msgSend(null, ptr, sel, backgroundImage)
}

fun NSSliderCell.image(): MemorySegment {
    val sel = ObjCRuntime.sel("image")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

