/**
 * Kotlin/JVM wrapper for Objective-C class: NSGridRow
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSGridRow(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGridRow") }
        
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
    
    // @property height
    fun height(): CGFloat {
        val sel = ObjCRuntime.sel("height")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property topPadding
    fun topPadding(): CGFloat {
        val sel = ObjCRuntime.sel("topPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTopPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTopPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bottomPadding
    fun bottomPadding(): CGFloat {
        val sel = ObjCRuntime.sel("bottomPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setBottomPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBottomPadding:")
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

