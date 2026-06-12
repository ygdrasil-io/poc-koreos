package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMatrix
 * Superclass: NSControl
 * Protocols: NSUserInterfaceValidations, NSViewToolTipOwner
 */
open class NSMatrix(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMatrix") }
        
    }
    
    override fun `initWithFrame`(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithFrame_mode_prototype_numberOfRows_numberOfColumns(frameRect: NSRect, mode: NSMatrixMode, cell: MemorySegment, rowsHigh: NSInteger, colsWide: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:mode:prototype:numberOfRows:numberOfColumns:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), mode, cell, rowsHigh, colsWide) as MemorySegment
    }
    
    fun initWithFrame_mode_cellClass_numberOfRows_numberOfColumns(frameRect: NSRect, mode: NSMatrixMode, factoryId: Class<*>, rowsHigh: NSInteger, colsWide: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:mode:cellClass:numberOfRows:numberOfColumns:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), mode, factoryId, rowsHigh, colsWide) as MemorySegment
    }
    
    fun makeCellAtRow_column(row: NSInteger, col: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("makeCellAtRow:column:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, col) as MemorySegment
    }
    
    fun sendAction_to_forAllCells(selector: MemorySegment, `object`: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("sendAction:to:forAllCells:")
        ObjCRuntime.msgSend(null, ptr, sel, selector, `object`, flag)
    }
    
    fun sortUsingSelector(comparator: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("sortUsingSelector:")
        ObjCRuntime.msgSend(null, ptr, sel, comparator)
    }
    
    fun sortUsingFunction_context(compare: MemorySegment, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("sortUsingFunction:context:")
        ObjCRuntime.msgSend(null, ptr, sel, compare, context)
    }
    
    fun setSelectionFrom_to_anchor_highlight(startPos: NSInteger, endPos: NSInteger, anchorPos: NSInteger, lit: BOOL): Unit {
        val sel = ObjCRuntime.sel("setSelectionFrom:to:anchor:highlight:")
        ObjCRuntime.msgSend(null, ptr, sel, startPos, endPos, anchorPos, lit)
    }
    
    fun deselectSelectedCell(): Unit {
        val sel = ObjCRuntime.sel("deselectSelectedCell")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun deselectAllCells(): Unit {
        val sel = ObjCRuntime.sel("deselectAllCells")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun selectCellAtRow_column(row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectCellAtRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, row, col)
    }
    
    fun selectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectCellWithTag(tag: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("selectCellWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tag) as BOOL
    }
    
    fun setScrollable(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setScrollable:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun setState_atRow_column(value: NSInteger, row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setState:atRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, value, row, col)
    }
    
    fun getNumberOfRows_columns(rowCount: MemorySegment, colCount: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getNumberOfRows:columns:")
        ObjCRuntime.msgSend(null, ptr, sel, rowCount, colCount)
    }
    
    fun cellAtRow_column(row: NSInteger, col: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("cellAtRow:column:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, col) as MemorySegment
    }
    
    fun cellFrameAtRow_column(row: NSInteger, col: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("cellFrameAtRow:column:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, row, col) as NSRect
    }
    
    fun getRow_column_ofCell(row: MemorySegment, col: MemorySegment, cell: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getRow:column:ofCell:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, row, col, cell) as BOOL
    }
    
    fun getRow_column_forPoint(row: MemorySegment, col: MemorySegment, point: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("getRow:column:forPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, row, col, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    fun renewRows_columns(newRows: NSInteger, newCols: NSInteger): Unit {
        val sel = ObjCRuntime.sel("renewRows:columns:")
        ObjCRuntime.msgSend(null, ptr, sel, newRows, newCols)
    }
    
    fun putCell_atRow_column(newCell: MemorySegment, row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("putCell:atRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, newCell, row, col)
    }
    
    fun addRow(): Unit {
        val sel = ObjCRuntime.sel("addRow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addRowWithCells(newCells: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addRowWithCells:")
        ObjCRuntime.msgSend(null, ptr, sel, newCells)
    }
    
    fun insertRow(row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertRow:")
        ObjCRuntime.msgSend(null, ptr, sel, row)
    }
    
    fun insertRow_withCells(row: NSInteger, newCells: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertRow:withCells:")
        ObjCRuntime.msgSend(null, ptr, sel, row, newCells)
    }
    
    fun removeRow(row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeRow:")
        ObjCRuntime.msgSend(null, ptr, sel, row)
    }
    
    fun addColumn(): Unit {
        val sel = ObjCRuntime.sel("addColumn")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addColumnWithCells(newCells: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addColumnWithCells:")
        ObjCRuntime.msgSend(null, ptr, sel, newCells)
    }
    
    fun insertColumn(column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, column)
    }
    
    fun insertColumn_withCells(column: NSInteger, newCells: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertColumn:withCells:")
        ObjCRuntime.msgSend(null, ptr, sel, column, newCells)
    }
    
    fun removeColumn(col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, col)
    }
    
    fun cellWithTag(tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("cellWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tag) as MemorySegment
    }
    
    fun sizeToCells(): Unit {
        val sel = ObjCRuntime.sel("sizeToCells")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setValidateSize(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setValidateSize:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun drawCellAtRow_column(row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("drawCellAtRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, row, col)
    }
    
    fun highlightCell_atRow_column(flag: BOOL, row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("highlightCell:atRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, row, col)
    }
    
    fun scrollCellToVisibleAtRow_column(row: NSInteger, col: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollCellToVisibleAtRow:column:")
        ObjCRuntime.msgSend(null, ptr, sel, row, col)
    }
    
    fun mouseDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun performKeyEquivalent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performKeyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun sendAction(): BOOL {
        val sel = ObjCRuntime.sel("sendAction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun sendDoubleAction(): Unit {
        val sel = ObjCRuntime.sel("sendDoubleAction")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun textShouldBeginEditing(textObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("textShouldBeginEditing:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
    }
    
    fun textShouldEndEditing(textObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("textShouldEndEditing:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
    }
    
    fun textDidBeginEditing(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidBeginEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun textDidEndEditing(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidEndEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun textDidChange(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidChange:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun selectText(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectText:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectTextAtRow_column(row: NSInteger, col: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("selectTextAtRow:column:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, col) as MemorySegment
    }
    
    fun acceptsFirstMouse(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("acceptsFirstMouse:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun resetCursorRects(): Unit {
        val sel = ObjCRuntime.sel("resetCursorRects")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setToolTip_forCell(toolTipString: MemorySegment, cell: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setToolTip:forCell:")
        ObjCRuntime.msgSend(null, ptr, sel, toolTipString, cell)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setToolTip_forCell(toolTipString: String, cell: MemorySegment): Unit = setToolTip_forCell(ObjCRuntime.newNSString(Arena.global(), toolTipString), cell)
    
    fun toolTipForCell(cell: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("toolTipForCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cell) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun toolTipForCellAsString(cell: MemorySegment): String = ObjCRuntime.toJavaString(toolTipForCell(cell))
    
    // @property cellClass
    override fun `cellClass`(): Class<*> {
        val sel = ObjCRuntime.sel("cellClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
    }
    override fun `setCellClass`(value: Class<*>) {
        val sel = ObjCRuntime.sel("setCellClass:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prototype
    fun prototype(): MemorySegment {
        val sel = ObjCRuntime.sel("prototype")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrototype(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrototype:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mode
    fun mode(): NSMatrixMode {
        val sel = ObjCRuntime.sel("mode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMatrixMode
    }
    fun setMode(value: NSMatrixMode) {
        val sel = ObjCRuntime.sel("setMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsEmptySelection
    fun allowsEmptySelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsEmptySelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsEmptySelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsEmptySelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cells
    /** @return NSArray<NSCell *> * */
    fun cells(): MemorySegment {
        val sel = ObjCRuntime.sel("cells")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedCell
    override fun `selectedCell`(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedCells
    /** @return NSArray<__kindof NSCell *> * */
    fun selectedCells(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedCells")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedRow
    fun selectedRow(): NSInteger {
        val sel = ObjCRuntime.sel("selectedRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedColumn
    fun selectedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("selectedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectionByRect
    fun isSelectionByRect(): BOOL {
        val sel = ObjCRuntime.sel("isSelectionByRect")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectionByRect(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectionByRect:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cellSize
    fun cellSize(): NSSize {
        val sel = ObjCRuntime.sel("cellSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setCellSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setCellSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property intercellSpacing
    fun intercellSpacing(): NSSize {
        val sel = ObjCRuntime.sel("intercellSpacing")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setIntercellSpacing(value: NSSize) {
        val sel = ObjCRuntime.sel("setIntercellSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
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
    
    // @property cellBackgroundColor
    fun cellBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("cellBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCellBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCellBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsCellBackground
    fun drawsCellBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsCellBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsCellBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsCellBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsBackground
    fun drawsBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property doubleAction
    fun doubleAction(): MemorySegment {
        val sel = ObjCRuntime.sel("doubleAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDoubleAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDoubleAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosizesCells
    fun autosizesCells(): BOOL {
        val sel = ObjCRuntime.sel("autosizesCells")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutosizesCells(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutosizesCells:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoscroll
    fun isAutoscroll(): BOOL {
        val sel = ObjCRuntime.sel("isAutoscroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutoscroll(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutoscroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mouseDownFlags
    fun mouseDownFlags(): NSInteger {
        val sel = ObjCRuntime.sel("mouseDownFlags")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property delegate
    /** @return id<NSMatrixDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autorecalculatesCellSize
    fun autorecalculatesCellSize(): BOOL {
        val sel = ObjCRuntime.sel("autorecalculatesCellSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutorecalculatesCellSize(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutorecalculatesCellSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSKeyboardUI on NSMatrix ─────────────────────────────────────────

fun NSMatrix.tabKeyTraversesCells(): BOOL {
    val sel = ObjCRuntime.sel("tabKeyTraversesCells")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSMatrix.setTabKeyTraversesCells(tabKeyTraversesCells: BOOL): Unit {
    val sel = ObjCRuntime.sel("setTabKeyTraversesCells:")
    ObjCRuntime.msgSend(null, ptr, sel, tabKeyTraversesCells)
}

fun NSMatrix.keyCell(): MemorySegment {
    val sel = ObjCRuntime.sel("keyCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMatrix.setKeyCell(keyCell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setKeyCell:")
    ObjCRuntime.msgSend(null, ptr, sel, keyCell)
}

// @property tabKeyTraversesCells
fun NSMatrix.tabKeyTraversesCells(): BOOL {
    val sel = ObjCRuntime.sel("tabKeyTraversesCells")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSMatrix.setTabKeyTraversesCells(value: BOOL) {
    val sel = ObjCRuntime.sel("setTabKeyTraversesCells:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property keyCell
fun NSMatrix.keyCell(): MemorySegment {
    val sel = ObjCRuntime.sel("keyCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSMatrix.setKeyCell(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setKeyCell:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

