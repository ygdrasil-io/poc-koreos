package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableRowView
 * Superclass: NSView
 * Protocols: NSAccessibilityRow
 */
open class NSTableRowView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableRowView") }
        
    }
    
    fun drawBackgroundInRect(dirtyRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawBackgroundInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(dirtyRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawSelectionInRect(dirtyRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawSelectionInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(dirtyRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawSeparatorInRect(dirtyRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawSeparatorInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(dirtyRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawDraggingDestinationFeedbackInRect(dirtyRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawDraggingDestinationFeedbackInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(dirtyRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun viewAtColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("viewAtColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    // @property selectionHighlightStyle
    fun selectionHighlightStyle(): NSTableViewSelectionHighlightStyle {
        val sel = ObjCRuntime.sel("selectionHighlightStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewSelectionHighlightStyle
    }
    fun setSelectionHighlightStyle(value: NSTableViewSelectionHighlightStyle) {
        val sel = ObjCRuntime.sel("setSelectionHighlightStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property emphasized
    fun isEmphasized(): BOOL {
        val sel = ObjCRuntime.sel("isEmphasized")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEmphasized(value: BOOL) {
        val sel = ObjCRuntime.sel("setEmphasized:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property groupRowStyle
    fun isGroupRowStyle(): BOOL {
        val sel = ObjCRuntime.sel("isGroupRowStyle")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setGroupRowStyle(value: BOOL) {
        val sel = ObjCRuntime.sel("setGroupRowStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selected
    fun isSelected(): BOOL {
        val sel = ObjCRuntime.sel("isSelected")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelected(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelected:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property previousRowSelected
    fun isPreviousRowSelected(): BOOL {
        val sel = ObjCRuntime.sel("isPreviousRowSelected")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPreviousRowSelected(value: BOOL) {
        val sel = ObjCRuntime.sel("setPreviousRowSelected:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property nextRowSelected
    fun isNextRowSelected(): BOOL {
        val sel = ObjCRuntime.sel("isNextRowSelected")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNextRowSelected(value: BOOL) {
        val sel = ObjCRuntime.sel("setNextRowSelected:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property floating
    fun isFloating(): BOOL {
        val sel = ObjCRuntime.sel("isFloating")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFloating(value: BOOL) {
        val sel = ObjCRuntime.sel("setFloating:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property targetForDropOperation
    fun isTargetForDropOperation(): BOOL {
        val sel = ObjCRuntime.sel("isTargetForDropOperation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTargetForDropOperation(value: BOOL) {
        val sel = ObjCRuntime.sel("setTargetForDropOperation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggingDestinationFeedbackStyle
    fun draggingDestinationFeedbackStyle(): NSTableViewDraggingDestinationFeedbackStyle {
        val sel = ObjCRuntime.sel("draggingDestinationFeedbackStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewDraggingDestinationFeedbackStyle
    }
    fun setDraggingDestinationFeedbackStyle(value: NSTableViewDraggingDestinationFeedbackStyle) {
        val sel = ObjCRuntime.sel("setDraggingDestinationFeedbackStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indentationForDropOperation
    fun indentationForDropOperation(): CGFloat {
        val sel = ObjCRuntime.sel("indentationForDropOperation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setIndentationForDropOperation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setIndentationForDropOperation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interiorBackgroundStyle
    fun interiorBackgroundStyle(): NSBackgroundStyle {
        val sel = ObjCRuntime.sel("interiorBackgroundStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfColumns
    fun numberOfColumns(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

