package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextBlock
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSTextBlock(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextBlock") }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun setValue_type_forDimension(`val`: CGFloat, type: NSTextBlockValueType, dimension: NSTextBlockDimension): Unit {
        val sel = ObjCRuntime.sel("setValue:type:forDimension:")
        ObjCRuntime.msgSend(null, ptr, sel, `val`, type, dimension)
    }
    
    open fun valueForDimension(dimension: NSTextBlockDimension): CGFloat {
        val sel = ObjCRuntime.sel("valueForDimension:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, dimension) as CGFloat
    }
    
    open fun valueTypeForDimension(dimension: NSTextBlockDimension): NSTextBlockValueType {
        val sel = ObjCRuntime.sel("valueTypeForDimension:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dimension) as NSTextBlockValueType
    }
    
    open fun setContentWidth_type(`val`: CGFloat, type: NSTextBlockValueType): Unit {
        val sel = ObjCRuntime.sel("setContentWidth:type:")
        ObjCRuntime.msgSend(null, ptr, sel, `val`, type)
    }
    
    open fun setWidth_type_forLayer_edge(`val`: CGFloat, type: NSTextBlockValueType, layer: NSTextBlockLayer, edge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("setWidth:type:forLayer:edge:")
        ObjCRuntime.msgSend(null, ptr, sel, `val`, type, layer, edge)
    }
    
    open fun setWidth_type_forLayer(`val`: CGFloat, type: NSTextBlockValueType, layer: NSTextBlockLayer): Unit {
        val sel = ObjCRuntime.sel("setWidth:type:forLayer:")
        ObjCRuntime.msgSend(null, ptr, sel, `val`, type, layer)
    }
    
    open fun widthForLayer_edge(layer: NSTextBlockLayer, edge: NSRectEdge): CGFloat {
        val sel = ObjCRuntime.sel("widthForLayer:edge:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, layer, edge) as CGFloat
    }
    
    open fun widthValueTypeForLayer_edge(layer: NSTextBlockLayer, edge: NSRectEdge): NSTextBlockValueType {
        val sel = ObjCRuntime.sel("widthValueTypeForLayer:edge:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, layer, edge) as NSTextBlockValueType
    }
    
    open fun setBorderColor_forEdge(color: MemorySegment, edge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("setBorderColor:forEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, color, edge)
    }
    
    open fun setBorderColor(color: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setBorderColor:")
        ObjCRuntime.msgSend(null, ptr, sel, color)
    }
    
    open fun borderColorForEdge(edge: NSRectEdge): MemorySegment {
        val sel = ObjCRuntime.sel("borderColorForEdge:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, edge) as MemorySegment
    }
    
    open fun rectForLayoutAtPoint_inRect_textContainer_characterRange(startingPoint: NSPoint, rect: NSRect, textContainer: MemorySegment, charRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("rectForLayoutAtPoint:inRect:textContainer:characterRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(startingPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), textContainer, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    open fun boundsRectForContentRect_inRect_textContainer_characterRange(contentRect: NSRect, rect: NSRect, textContainer: MemorySegment, charRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("boundsRectForContentRect:inRect:textContainer:characterRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(contentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), textContainer, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    open fun drawBackgroundWithFrame_inView_characterRange_layoutManager(frameRect: NSRect, controlView: MemorySegment, charRange: NSRange, layoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawBackgroundWithFrame:inView:characterRange:layoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), layoutManager)
    }
    
    // @property contentWidth
    open fun contentWidth(): CGFloat {
        val sel = ObjCRuntime.sel("contentWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property contentWidthValueType
    open fun contentWidthValueType(): NSTextBlockValueType {
        val sel = ObjCRuntime.sel("contentWidthValueType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextBlockValueType
    }
    
    // @property verticalAlignment
    open fun verticalAlignment(): NSTextBlockVerticalAlignment {
        val sel = ObjCRuntime.sel("verticalAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextBlockVerticalAlignment
    }
    open fun setVerticalAlignment(value: NSTextBlockVerticalAlignment) {
        val sel = ObjCRuntime.sel("setVerticalAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    open fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

