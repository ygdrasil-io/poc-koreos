/**
 * Kotlin/JVM wrapper for Objective-C class: NSGridCell
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSGridCell(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGridCell") }
        
        fun emptyContentView(): MemorySegment {
            val sel = ObjCRuntime.sel("emptyContentView")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property contentView
    fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property emptyContentView
    fun emptyContentView(): MemorySegment {
        val sel = ObjCRuntime.sel("emptyContentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property row
    fun row(): MemorySegment {
        val sel = ObjCRuntime.sel("row")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property column
    fun column(): MemorySegment {
        val sel = ObjCRuntime.sel("column")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property xPlacement
    fun xPlacement(): NSGridCellPlacement {
        val sel = ObjCRuntime.sel("xPlacement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridCellPlacement
    }
    fun setXPlacement(value: NSGridCellPlacement) {
        val sel = ObjCRuntime.sel("setXPlacement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property yPlacement
    fun yPlacement(): NSGridCellPlacement {
        val sel = ObjCRuntime.sel("yPlacement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridCellPlacement
    }
    fun setYPlacement(value: NSGridCellPlacement) {
        val sel = ObjCRuntime.sel("setYPlacement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowAlignment
    fun rowAlignment(): NSGridRowAlignment {
        val sel = ObjCRuntime.sel("rowAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGridRowAlignment
    }
    fun setRowAlignment(value: NSGridRowAlignment) {
        val sel = ObjCRuntime.sel("setRowAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customPlacementConstraints
    /** @return NSArray<NSLayoutConstraint *> * */
    fun customPlacementConstraints(): MemorySegment {
        val sel = ObjCRuntime.sel("customPlacementConstraints")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomPlacementConstraints(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomPlacementConstraints:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

