package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGridCell
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSGridCell(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGridCell") }
        
        open fun emptyContentView(): MemorySegment {
            val sel = ObjCRuntime.sel("emptyContentView")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property contentView
    open fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property emptyContentView
    open fun row(): MemorySegment {
        val sel = ObjCRuntime.sel("row")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property column
    open fun column(): MemorySegment {
        val sel = ObjCRuntime.sel("column")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property xPlacement
    open fun xPlacement(): NSGridCellPlacement {
        val sel = ObjCRuntime.sel("xPlacement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridCellPlacement
    }
    open fun setXPlacement(value: NSGridCellPlacement) {
        val sel = ObjCRuntime.sel("setXPlacement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property yPlacement
    open fun yPlacement(): NSGridCellPlacement {
        val sel = ObjCRuntime.sel("yPlacement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridCellPlacement
    }
    open fun setYPlacement(value: NSGridCellPlacement) {
        val sel = ObjCRuntime.sel("setYPlacement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowAlignment
    open fun rowAlignment(): NSGridRowAlignment {
        val sel = ObjCRuntime.sel("rowAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridRowAlignment
    }
    open fun setRowAlignment(value: NSGridRowAlignment) {
        val sel = ObjCRuntime.sel("setRowAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customPlacementConstraints
    /** @return NSArray<NSLayoutConstraint *> * */
    open fun customPlacementConstraints(): MemorySegment {
        val sel = ObjCRuntime.sel("customPlacementConstraints")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCustomPlacementConstraints(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomPlacementConstraints:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

