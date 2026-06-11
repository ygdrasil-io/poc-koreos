/**
 * Kotlin/JVM wrapper for Objective-C class: NSGridView
 * Superclass: NSView
 */
open class NSGridView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGridView") }
        
        fun gridViewWithNumberOfColumns_rows(columnCount: NSInteger, rowCount: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("gridViewWithNumberOfColumns:rows:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, columnCount, rowCount) as MemorySegment
        }
        
        fun gridViewWithViews(rows: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("gridViewWithViews:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, rows) as MemorySegment
        }
        
    }
    
    fun initWithFrame(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun rowAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("rowAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun indexOfRow(row: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfRow:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, row) as NSInteger
    }
    
    fun columnAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("columnAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun indexOfColumn(column: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfColumn:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, column) as NSInteger
    }
    
    fun cellAtColumnIndex_rowIndex(columnIndex: NSInteger, rowIndex: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("cellAtColumnIndex:rowIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, columnIndex, rowIndex) as MemorySegment
    }
    
    fun cellForView(view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("cellForView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view) as MemorySegment
    }
    
    fun addRowWithViews(views: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addRowWithViews:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, views) as MemorySegment
    }
    
    fun insertRowAtIndex_withViews(index: NSInteger, views: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("insertRowAtIndex:withViews:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, views) as MemorySegment
    }
    
    fun moveRowAtIndex_toIndex(fromIndex: NSInteger, toIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveRowAtIndex:toIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, fromIndex, toIndex)
    }
    
    fun removeRowAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeRowAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun addColumnWithViews(views: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addColumnWithViews:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, views) as MemorySegment
    }
    
    fun insertColumnAtIndex_withViews(index: NSInteger, views: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("insertColumnAtIndex:withViews:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, views) as MemorySegment
    }
    
    fun moveColumnAtIndex_toIndex(fromIndex: NSInteger, toIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveColumnAtIndex:toIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, fromIndex, toIndex)
    }
    
    fun removeColumnAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeColumnAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun mergeCellsInHorizontalRange_verticalRange(hRange: NSRange, vRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("mergeCellsInHorizontalRange:verticalRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(hRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(vRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    // @property numberOfRows
    fun numberOfRows(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfColumns
    fun numberOfColumns(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColumns")
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
    
    // @property rowSpacing
    fun rowSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("rowSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRowSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRowSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property columnSpacing
    fun columnSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("columnSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setColumnSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setColumnSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

