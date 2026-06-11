/**
 * Kotlin/JVM wrapper for Objective-C class: NSGridColumn
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSGridColumn(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGridColumn") }
        
    }
    
    fun cellAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("cellAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun mergeCellsInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("mergeCellsInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    // @property gridView
    fun gridView(): MemorySegment {
        val sel = ObjCRuntime.sel("gridView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfCells
    fun numberOfCells(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfCells")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
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
    
    // @property width
    fun width(): CGFloat {
        val sel = ObjCRuntime.sel("width")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property leadingPadding
    fun leadingPadding(): CGFloat {
        val sel = ObjCRuntime.sel("leadingPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLeadingPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLeadingPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property trailingPadding
    fun trailingPadding(): CGFloat {
        val sel = ObjCRuntime.sel("trailingPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTrailingPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTrailingPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

