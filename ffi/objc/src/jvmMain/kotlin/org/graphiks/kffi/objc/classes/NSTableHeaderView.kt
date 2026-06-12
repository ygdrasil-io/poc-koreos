package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableHeaderView
 * Superclass: NSView
 * Protocols: NSViewToolTipOwner
 */
open class NSTableHeaderView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableHeaderView") }
        
    }
    
    fun headerRectOfColumn(column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("headerRectOfColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column) as NSRect
    }
    
    fun columnAtPoint(point: NSPoint): NSInteger {
        val sel = ObjCRuntime.sel("columnAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSInteger
    }
    
    // @property tableView
    fun tableView(): MemorySegment {
        val sel = ObjCRuntime.sel("tableView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTableView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTableView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggedColumn
    fun draggedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("draggedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property draggedDistance
    fun draggedDistance(): CGFloat {
        val sel = ObjCRuntime.sel("draggedDistance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property resizedColumn
    fun resizedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("resizedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

