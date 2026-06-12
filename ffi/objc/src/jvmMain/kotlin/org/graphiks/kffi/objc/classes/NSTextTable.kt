package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextTable
 * Superclass: NSTextBlock
 */
open class NSTextTable(ptr: MemorySegment) : NSTextBlock(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextTable") }
        
    }
    
    fun rectForBlock_layoutAtPoint_inRect_textContainer_characterRange(block: MemorySegment, startingPoint: NSPoint, rect: NSRect, textContainer: MemorySegment, charRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("rectForBlock:layoutAtPoint:inRect:textContainer:characterRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, ObjCRuntime.ObjCStructArg(startingPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), textContainer, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    fun boundsRectForBlock_contentRect_inRect_textContainer_characterRange(block: MemorySegment, contentRect: NSRect, rect: NSRect, textContainer: MemorySegment, charRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("boundsRectForBlock:contentRect:inRect:textContainer:characterRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, ObjCRuntime.ObjCStructArg(contentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), textContainer, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    fun drawBackgroundForBlock_withFrame_inView_characterRange_layoutManager(block: MemorySegment, frameRect: NSRect, controlView: MemorySegment, charRange: NSRange, layoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawBackgroundForBlock:withFrame:inView:characterRange:layoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, block, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), layoutManager)
    }
    
    // @property numberOfColumns
    fun numberOfColumns(): NSUInteger {
        val sel = ObjCRuntime.sel("numberOfColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setNumberOfColumns(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setNumberOfColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layoutAlgorithm
    fun layoutAlgorithm(): NSTextTableLayoutAlgorithm {
        val sel = ObjCRuntime.sel("layoutAlgorithm")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextTableLayoutAlgorithm
    }
    fun setLayoutAlgorithm(value: NSTextTableLayoutAlgorithm) {
        val sel = ObjCRuntime.sel("setLayoutAlgorithm:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsesBorders
    fun collapsesBorders(): BOOL {
        val sel = ObjCRuntime.sel("collapsesBorders")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCollapsesBorders(value: BOOL) {
        val sel = ObjCRuntime.sel("setCollapsesBorders:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidesEmptyCells
    fun hidesEmptyCells(): BOOL {
        val sel = ObjCRuntime.sel("hidesEmptyCells")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidesEmptyCells(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidesEmptyCells:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

