/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextTableBlock
 * Superclass: NSTextBlock
 */
open class NSTextTableBlock(ptr: MemorySegment) : NSTextBlock(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextTableBlock") }
        
    }
    
    fun initWithTable_startingRow_rowSpan_startingColumn_columnSpan(table: MemorySegment, row: NSInteger, rowSpan: NSInteger, col: NSInteger, colSpan: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTable:startingRow:rowSpan:startingColumn:columnSpan:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, table, row, rowSpan, col, colSpan) as MemorySegment
    }
    
    // @property table
    fun table(): MemorySegment {
        val sel = ObjCRuntime.sel("table")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property startingRow
    fun startingRow(): NSInteger {
        val sel = ObjCRuntime.sel("startingRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property rowSpan
    fun rowSpan(): NSInteger {
        val sel = ObjCRuntime.sel("rowSpan")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property startingColumn
    fun startingColumn(): NSInteger {
        val sel = ObjCRuntime.sel("startingColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property columnSpan
    fun columnSpan(): NSInteger {
        val sel = ObjCRuntime.sel("columnSpan")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

