package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScroller
 * Superclass: NSControl
 */
open class NSScroller(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScroller") }
        
        fun scrollerWidthForControlSize_scrollerStyle(controlSize: NSControlSize, scrollerStyle: NSScrollerStyle): CGFloat {
            val sel = ObjCRuntime.sel("scrollerWidthForControlSize:scrollerStyle:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel, controlSize, scrollerStyle) as CGFloat
        }
        
        fun isCompatibleWithOverlayScrollers(): BOOL {
            val sel = ObjCRuntime.sel("isCompatibleWithOverlayScrollers")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun preferredScrollerStyle(): NSScrollerStyle {
            val sel = ObjCRuntime.sel("preferredScrollerStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSScrollerStyle
        }
        
    }
    
    fun rectForPart(partCode: NSScrollerPart): NSRect {
        val sel = ObjCRuntime.sel("rectForPart:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, partCode) as NSRect
    }
    
    fun checkSpaceForParts(): Unit {
        val sel = ObjCRuntime.sel("checkSpaceForParts")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun drawKnob(): Unit {
        val sel = ObjCRuntime.sel("drawKnob")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun drawKnobSlotInRect_highlight(slotRect: NSRect, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("drawKnobSlotInRect:highlight:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(slotRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), flag)
    }
    
    fun testPart(point: NSPoint): NSScrollerPart {
        val sel = ObjCRuntime.sel("testPart:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSScrollerPart
    }
    
    fun trackKnob(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("trackKnob:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    // @property compatibleWithOverlayScrollers
    fun isCompatibleWithOverlayScrollers(): BOOL {
        val sel = ObjCRuntime.sel("isCompatibleWithOverlayScrollers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property preferredScrollerStyle
    fun preferredScrollerStyle(): NSScrollerStyle {
        val sel = ObjCRuntime.sel("preferredScrollerStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerStyle
    }
    
    // @property scrollerStyle
    fun scrollerStyle(): NSScrollerStyle {
        val sel = ObjCRuntime.sel("scrollerStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerStyle
    }
    fun setScrollerStyle(value: NSScrollerStyle) {
        val sel = ObjCRuntime.sel("setScrollerStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property knobStyle
    fun knobStyle(): NSScrollerKnobStyle {
        val sel = ObjCRuntime.sel("knobStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerKnobStyle
    }
    fun setKnobStyle(value: NSScrollerKnobStyle) {
        val sel = ObjCRuntime.sel("setKnobStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usableParts
    fun usableParts(): NSUsableScrollerParts {
        val sel = ObjCRuntime.sel("usableParts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUsableScrollerParts
    }
    
    // @property controlSize
    override fun `controlSize`(): NSControlSize {
        val sel = ObjCRuntime.sel("controlSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlSize
    }
    override fun `setControlSize`(value: NSControlSize) {
        val sel = ObjCRuntime.sel("setControlSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hitPart
    fun hitPart(): NSScrollerPart {
        val sel = ObjCRuntime.sel("hitPart")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerPart
    }
    
    // @property knobProportion
    fun knobProportion(): CGFloat {
        val sel = ObjCRuntime.sel("knobProportion")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setKnobProportion(value: CGFloat) {
        val sel = ObjCRuntime.sel("setKnobProportion:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSScroller ─────────────────────────────────────────

fun NSScroller.setFloatValue_knobProportion(value: Float, proportion: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setFloatValue:knobProportion:")
    ObjCRuntime.msgSend(null, ptr, sel, value, proportion)
}

fun NSScroller.highlight(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("highlight:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSScroller.trackScrollButtons(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("trackScrollButtons:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSScroller.drawParts(): Unit {
    val sel = ObjCRuntime.sel("drawParts")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSScroller.drawArrow_highlight(whichArrow: NSScrollerArrow, flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("drawArrow:highlight:")
    ObjCRuntime.msgSend(null, ptr, sel, whichArrow, flag)
}

fun NSScroller.arrowsPosition(): NSScrollArrowPosition {
    val sel = ObjCRuntime.sel("arrowsPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollArrowPosition
}

fun NSScroller.setArrowsPosition(arrowsPosition: NSScrollArrowPosition): Unit {
    val sel = ObjCRuntime.sel("setArrowsPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, arrowsPosition)
}

fun NSScroller.controlTint(): NSControlTint {
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}

fun NSScroller.setControlTint(controlTint: NSControlTint): Unit {
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, controlTint)
}

// Class<*> method: +[NSScroller scrollerWidthForControlSize:]
fun NSScroller_scrollerWidthForControlSize(controlSize: NSControlSize): CGFloat {
    val sel = ObjCRuntime.sel("scrollerWidthForControlSize:")
    val cls = ObjCRuntime.getClass("NSScroller")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, cls, sel, controlSize) as CGFloat
}

// Class<*> method: +[NSScroller scrollerWidth]
fun NSScroller_scrollerWidth(): CGFloat {
    val sel = ObjCRuntime.sel("scrollerWidth")
    val cls = ObjCRuntime.getClass("NSScroller")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, cls, sel) as CGFloat
}

// @property arrowsPosition
fun NSScroller.arrowsPosition(): NSScrollArrowPosition {
    val sel = ObjCRuntime.sel("arrowsPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollArrowPosition
}
fun NSScroller.setArrowsPosition(value: NSScrollArrowPosition) {
    val sel = ObjCRuntime.sel("setArrowsPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property controlTint
fun NSScroller.controlTint(): NSControlTint {
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}
fun NSScroller.setControlTint(value: NSControlTint) {
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

