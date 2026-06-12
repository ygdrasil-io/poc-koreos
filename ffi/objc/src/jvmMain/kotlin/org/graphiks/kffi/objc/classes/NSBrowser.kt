package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSBrowser
 * Superclass: NSControl
 */
open class NSBrowser(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBrowser") }
        
        fun removeSavedColumnsWithAutosaveName(name: NSBrowserColumnsAutosaveName): Unit {
            val sel = ObjCRuntime.sel("removeSavedColumnsWithAutosaveName:")
            ObjCRuntime.msgSend(null, _class, sel, name)
        }
        
        override fun `cellClass`(): Class<*> {
            val sel = ObjCRuntime.sel("cellClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class<*>
        }
        
    }
    
    fun loadColumnZero(): Unit {
        val sel = ObjCRuntime.sel("loadColumnZero")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    override fun `setCellClass`(factoryId: Class<*>): Unit {
        val sel = ObjCRuntime.sel("setCellClass:")
        ObjCRuntime.msgSend(null, ptr, sel, factoryId)
    }
    
    fun itemAtIndexPath(indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
    }
    
    fun itemAtRow_inColumn(row: NSInteger, column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtRow:inColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, column) as MemorySegment
    }
    
    fun indexPathForColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathForColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    fun isLeafItem(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isLeafItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    fun reloadDataForRowIndexes_inColumn(rowIndexes: MemorySegment, column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("reloadDataForRowIndexes:inColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, rowIndexes, column)
    }
    
    fun parentForItemsInColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("parentForItemsInColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    fun scrollRowToVisible_inColumn(row: NSInteger, column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollRowToVisible:inColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, row, column)
    }
    
    fun setTitle_ofColumn(string: MemorySegment, column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setTitle:ofColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, string, column)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setTitle_ofColumn(string: String, column: NSInteger): Unit = setTitle_ofColumn(ObjCRuntime.newNSString(Arena.global(), string), column)
    
    fun titleOfColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("titleOfColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleOfColumnAsString(column: NSInteger): String = ObjCRuntime.toJavaString(titleOfColumn(column))
    
    fun setPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setPath(path: String): BOOL = setPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun path(): MemorySegment {
        val sel = ObjCRuntime.sel("path")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathAsString(): String = ObjCRuntime.toJavaString(path())
    
    fun pathToColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("pathToColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathToColumnAsString(column: NSInteger): String = ObjCRuntime.toJavaString(pathToColumn(column))
    
    fun selectedCellInColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("selectedCellInColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    fun selectRow_inColumn(row: NSInteger, column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectRow:inColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, row, column)
    }
    
    fun selectedRowInColumn(column: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("selectedRowInColumn:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, column) as NSInteger
    }
    
    fun selectRowIndexes_inColumn(indexes: MemorySegment, column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectRowIndexes:inColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, column)
    }
    
    fun selectedRowIndexesInColumn(column: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("selectedRowIndexesInColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
    }
    
    fun reloadColumn(column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("reloadColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, column)
    }
    
    fun validateVisibleColumns(): Unit {
        val sel = ObjCRuntime.sel("validateVisibleColumns")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scrollColumnsRightBy(shiftAmount: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollColumnsRightBy:")
        ObjCRuntime.msgSend(null, ptr, sel, shiftAmount)
    }
    
    fun scrollColumnsLeftBy(shiftAmount: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollColumnsLeftBy:")
        ObjCRuntime.msgSend(null, ptr, sel, shiftAmount)
    }
    
    fun scrollColumnToVisible(column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollColumnToVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, column)
    }
    
    fun addColumn(): Unit {
        val sel = ObjCRuntime.sel("addColumn")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun loadedCellAtRow_column(row: NSInteger, col: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("loadedCellAtRow:column:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, col) as MemorySegment
    }
    
    fun selectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun tile(): Unit {
        val sel = ObjCRuntime.sel("tile")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun doClick(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("doClick:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun doDoubleClick(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("doDoubleClick:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun sendAction(): BOOL {
        val sel = ObjCRuntime.sel("sendAction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun titleFrameOfColumn(column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("titleFrameOfColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column) as NSRect
    }
    
    fun drawTitleOfColumn_inRect(column: NSInteger, rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawTitleOfColumn:inRect:")
        ObjCRuntime.msgSend(null, ptr, sel, column, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun frameOfColumn(column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("frameOfColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column) as NSRect
    }
    
    fun frameOfInsideOfColumn(column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("frameOfInsideOfColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column) as NSRect
    }
    
    fun frameOfRow_inColumn(row: NSInteger, column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("frameOfRow:inColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, row, column) as NSRect
    }
    
    fun getRow_column_forPoint(row: MemorySegment, column: MemorySegment, point: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("getRow:column:forPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, row, column, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    fun columnWidthForColumnContentWidth(columnContentWidth: CGFloat): CGFloat {
        val sel = ObjCRuntime.sel("columnWidthForColumnContentWidth:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, columnContentWidth) as CGFloat
    }
    
    fun columnContentWidthForColumnWidth(columnWidth: CGFloat): CGFloat {
        val sel = ObjCRuntime.sel("columnContentWidthForColumnWidth:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, columnWidth) as CGFloat
    }
    
    fun setWidth_ofColumn(columnWidth: CGFloat, columnIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setWidth:ofColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, columnWidth, columnIndex)
    }
    
    fun widthOfColumn(column: NSInteger): CGFloat {
        val sel = ObjCRuntime.sel("widthOfColumn:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, column) as CGFloat
    }
    
    fun noteHeightOfRowsWithIndexesChanged_inColumn(indexSet: MemorySegment, columnIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("noteHeightOfRowsWithIndexesChanged:inColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, indexSet, columnIndex)
    }
    
    fun setDefaultColumnWidth(columnWidth: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setDefaultColumnWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, columnWidth)
    }
    
    fun defaultColumnWidth(): CGFloat {
        val sel = ObjCRuntime.sel("defaultColumnWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    fun canDragRowsWithIndexes_inColumn_withEvent(rowIndexes: MemorySegment, column: NSInteger, event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canDragRowsWithIndexes:inColumn:withEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, rowIndexes, column, event) as BOOL
    }
    
    fun draggingImageForRowsWithIndexes_inColumn_withEvent_offset(rowIndexes: MemorySegment, column: NSInteger, event: MemorySegment, dragImageOffset: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageForRowsWithIndexes:inColumn:withEvent:offset:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rowIndexes, column, event, dragImageOffset) as MemorySegment
    }
    
    fun setDraggingSourceOperationMask_forLocal(mask: NSDragOperation, isLocal: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDraggingSourceOperationMask:forLocal:")
        ObjCRuntime.msgSend(null, ptr, sel, mask, isLocal)
    }
    
    fun editItemAtIndexPath_withEvent_select(indexPath: MemorySegment, event: MemorySegment, select: BOOL): Unit {
        val sel = ObjCRuntime.sel("editItemAtIndexPath:withEvent:select:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPath, event, select)
    }
    
    // @property cellClass
    override fun `cellClass`(): Class<*> {
        val sel = ObjCRuntime.sel("cellClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
    }
    
    // @property loaded
    fun isLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
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
    
    // @property cellPrototype
    fun cellPrototype(): MemorySegment {
        val sel = ObjCRuntime.sel("cellPrototype")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCellPrototype(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCellPrototype:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSBrowserDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property reusesColumns
    fun reusesColumns(): BOOL {
        val sel = ObjCRuntime.sel("reusesColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setReusesColumns(value: BOOL) {
        val sel = ObjCRuntime.sel("setReusesColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasHorizontalScroller
    fun hasHorizontalScroller(): BOOL {
        val sel = ObjCRuntime.sel("hasHorizontalScroller")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasHorizontalScroller(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasHorizontalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autohidesScroller
    fun autohidesScroller(): BOOL {
        val sel = ObjCRuntime.sel("autohidesScroller")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutohidesScroller(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutohidesScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property separatesColumns
    fun separatesColumns(): BOOL {
        val sel = ObjCRuntime.sel("separatesColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSeparatesColumns(value: BOOL) {
        val sel = ObjCRuntime.sel("setSeparatesColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titled
    fun isTitled(): BOOL {
        val sel = ObjCRuntime.sel("isTitled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTitled(value: BOOL) {
        val sel = ObjCRuntime.sel("setTitled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minColumnWidth
    fun minColumnWidth(): CGFloat {
        val sel = ObjCRuntime.sel("minColumnWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinColumnWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinColumnWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxVisibleColumns
    fun maxVisibleColumns(): NSInteger {
        val sel = ObjCRuntime.sel("maxVisibleColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaxVisibleColumns(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaxVisibleColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsMultipleSelection
    fun allowsMultipleSelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsMultipleSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsMultipleSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsMultipleSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsBranchSelection
    fun allowsBranchSelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsBranchSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsBranchSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsBranchSelection:")
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
    
    // @property takesTitleFromPreviousColumn
    fun takesTitleFromPreviousColumn(): BOOL {
        val sel = ObjCRuntime.sel("takesTitleFromPreviousColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTakesTitleFromPreviousColumn(value: BOOL) {
        val sel = ObjCRuntime.sel("setTakesTitleFromPreviousColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sendsActionOnArrowKeys
    fun sendsActionOnArrowKeys(): BOOL {
        val sel = ObjCRuntime.sel("sendsActionOnArrowKeys")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSendsActionOnArrowKeys(value: BOOL) {
        val sel = ObjCRuntime.sel("setSendsActionOnArrowKeys:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pathSeparator
    fun pathSeparator(): MemorySegment {
        val sel = ObjCRuntime.sel("pathSeparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPathSeparator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPathSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathSeparatorAsString(): String = ObjCRuntime.toJavaString(pathSeparator())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPathSeparator(value: String) = setPathSeparator(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property clickedColumn
    fun clickedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("clickedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property clickedRow
    fun clickedRow(): NSInteger {
        val sel = ObjCRuntime.sel("clickedRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedColumn
    fun selectedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("selectedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedCell
    override fun `selectedCell`(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedCells
    /** @return NSArray<NSCell *> * */
    fun selectedCells(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedCells")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionIndexPath
    fun selectionIndexPath(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionIndexPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionIndexPaths
    /** @return NSArray<NSIndexPath *> * */
    fun selectionIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionIndexPaths(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lastColumn
    fun lastColumn(): NSInteger {
        val sel = ObjCRuntime.sel("lastColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setLastColumn(value: NSInteger) {
        val sel = ObjCRuntime.sel("setLastColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfVisibleColumns
    fun numberOfVisibleColumns(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfVisibleColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property firstVisibleColumn
    fun firstVisibleColumn(): NSInteger {
        val sel = ObjCRuntime.sel("firstVisibleColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property lastVisibleColumn
    fun lastVisibleColumn(): NSInteger {
        val sel = ObjCRuntime.sel("lastVisibleColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property titleHeight
    fun titleHeight(): CGFloat {
        val sel = ObjCRuntime.sel("titleHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property columnResizingType
    fun columnResizingType(): NSBrowserColumnResizingType {
        val sel = ObjCRuntime.sel("columnResizingType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBrowserColumnResizingType
    }
    fun setColumnResizingType(value: NSBrowserColumnResizingType) {
        val sel = ObjCRuntime.sel("setColumnResizingType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prefersAllColumnUserResizing
    fun prefersAllColumnUserResizing(): BOOL {
        val sel = ObjCRuntime.sel("prefersAllColumnUserResizing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPrefersAllColumnUserResizing(value: BOOL) {
        val sel = ObjCRuntime.sel("setPrefersAllColumnUserResizing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowHeight
    fun rowHeight(): CGFloat {
        val sel = ObjCRuntime.sel("rowHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRowHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRowHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property columnsAutosaveName
    fun columnsAutosaveName(): NSBrowserColumnsAutosaveName {
        val sel = ObjCRuntime.sel("columnsAutosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBrowserColumnsAutosaveName
    }
    fun setColumnsAutosaveName(value: NSBrowserColumnsAutosaveName) {
        val sel = ObjCRuntime.sel("setColumnsAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsTypeSelect
    fun allowsTypeSelect(): BOOL {
        val sel = ObjCRuntime.sel("allowsTypeSelect")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsTypeSelect(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsTypeSelect:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
}

// ── Category: NSDeprecated on NSBrowser ─────────────────────────────────────────

fun NSBrowser.setAcceptsArrowKeys(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAcceptsArrowKeys:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSBrowser.acceptsArrowKeys(): BOOL {
    val sel = ObjCRuntime.sel("acceptsArrowKeys")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSBrowser.displayColumn(column: NSInteger): Unit {
    val sel = ObjCRuntime.sel("displayColumn:")
    ObjCRuntime.msgSend(null, ptr, sel, column)
}

fun NSBrowser.displayAllColumns(): Unit {
    val sel = ObjCRuntime.sel("displayAllColumns")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSBrowser.scrollViaScroller(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("scrollViaScroller:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSBrowser.updateScroller(): Unit {
    val sel = ObjCRuntime.sel("updateScroller")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSBrowser.setMatrixClass(factoryId: Class<*>): Unit {
    val sel = ObjCRuntime.sel("setMatrixClass:")
    ObjCRuntime.msgSend(null, ptr, sel, factoryId)
}

fun NSBrowser.matrixClass(): Class<*> {
    val sel = ObjCRuntime.sel("matrixClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
}

fun NSBrowser.columnOfMatrix(matrix: MemorySegment): NSInteger {
    val sel = ObjCRuntime.sel("columnOfMatrix:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, matrix) as NSInteger
}

fun NSBrowser.matrixInColumn(column: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("matrixInColumn:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column) as MemorySegment
}

