/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableView
 * Superclass: NSControl
 * Protocols: NSUserInterfaceValidations, NSTextViewDelegate, NSDraggingSource, NSAccessibilityTable
 */
open class NSTableView(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableView") }
        
    }
    
    fun initWithFrame(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun noteHeightOfRowsWithIndexesChanged(indexSet: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noteHeightOfRowsWithIndexesChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, indexSet)
    }
    
    fun addTableColumn(tableColumn: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTableColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, tableColumn)
    }
    
    fun removeTableColumn(tableColumn: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeTableColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, tableColumn)
    }
    
    fun moveColumn_toColumn(oldIndex: NSInteger, newIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveColumn:toColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, oldIndex, newIndex)
    }
    
    fun columnWithIdentifier(identifier: NSUserInterfaceItemIdentifier): NSInteger {
        val sel = ObjCRuntime.sel("columnWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, identifier) as NSInteger
    }
    
    fun tableColumnWithIdentifier(identifier: NSUserInterfaceItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("tableColumnWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    fun tile(): Unit {
        val sel = ObjCRuntime.sel("tile")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun sizeLastColumnToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeLastColumnToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scrollRowToVisible(row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollRowToVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, row)
    }
    
    fun scrollColumnToVisible(column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollColumnToVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, column)
    }
    
    fun reloadData(): Unit {
        val sel = ObjCRuntime.sel("reloadData")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun noteNumberOfRowsChanged(): Unit {
        val sel = ObjCRuntime.sel("noteNumberOfRowsChanged")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun reloadDataForRowIndexes_columnIndexes(rowIndexes: MemorySegment, columnIndexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadDataForRowIndexes:columnIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, rowIndexes, columnIndexes)
    }
    
    fun setIndicatorImage_inTableColumn(image: MemorySegment, tableColumn: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setIndicatorImage:inTableColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, image, tableColumn)
    }
    
    fun indicatorImageInTableColumn(tableColumn: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indicatorImageInTableColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tableColumn) as MemorySegment
    }
    
    fun canDragRowsWithIndexes_atPoint(rowIndexes: MemorySegment, mouseDownPoint: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("canDragRowsWithIndexes:atPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, rowIndexes, ObjCRuntime.ObjCStructArg(mouseDownPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    fun dragImageForRowsWithIndexes_tableColumns_event_offset(dragRows: MemorySegment, tableColumns: MemorySegment, dragEvent: MemorySegment, dragImageOffset: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dragImageForRowsWithIndexes:tableColumns:event:offset:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dragRows, tableColumns, dragEvent, dragImageOffset) as MemorySegment
    }
    
    fun setDraggingSourceOperationMask_forLocal(mask: NSDragOperation, isLocal: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDraggingSourceOperationMask:forLocal:")
        ObjCRuntime.msgSend(null, ptr, sel, mask, isLocal)
    }
    
    fun setDropRow_dropOperation(row: NSInteger, dropOperation: NSTableViewDropOperation): Unit {
        val sel = ObjCRuntime.sel("setDropRow:dropOperation:")
        ObjCRuntime.msgSend(null, ptr, sel, row, dropOperation)
    }
    
    fun selectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun deselectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deselectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectColumnIndexes_byExtendingSelection(indexes: MemorySegment, extend: BOOL): Unit {
        val sel = ObjCRuntime.sel("selectColumnIndexes:byExtendingSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, extend)
    }
    
    fun selectRowIndexes_byExtendingSelection(indexes: MemorySegment, extend: BOOL): Unit {
        val sel = ObjCRuntime.sel("selectRowIndexes:byExtendingSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, extend)
    }
    
    fun deselectColumn(column: NSInteger): Unit {
        val sel = ObjCRuntime.sel("deselectColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, column)
    }
    
    fun deselectRow(row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("deselectRow:")
        ObjCRuntime.msgSend(null, ptr, sel, row)
    }
    
    fun isColumnSelected(column: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isColumnSelected:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, column) as BOOL
    }
    
    fun isRowSelected(row: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isRowSelected:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, row) as BOOL
    }
    
    fun rectOfColumn(column: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("rectOfColumn:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column) as NSRect
    }
    
    fun rectOfRow(row: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("rectOfRow:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, row) as NSRect
    }
    
    fun columnIndexesInRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("columnIndexesInRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun rowsInRect(rect: NSRect): NSRange {
        val sel = ObjCRuntime.sel("rowsInRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRange
    }
    
    fun columnAtPoint(point: NSPoint): NSInteger {
        val sel = ObjCRuntime.sel("columnAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSInteger
    }
    
    fun rowAtPoint(point: NSPoint): NSInteger {
        val sel = ObjCRuntime.sel("rowAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSInteger
    }
    
    fun frameOfCellAtColumn_row(column: NSInteger, row: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("frameOfCellAtColumn:row:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, column, row) as NSRect
    }
    
    fun editColumn_row_withEvent_select(column: NSInteger, row: NSInteger, event: MemorySegment, select: BOOL): Unit {
        val sel = ObjCRuntime.sel("editColumn:row:withEvent:select:")
        ObjCRuntime.msgSend(null, ptr, sel, column, row, event, select)
    }
    
    fun drawRow_clipRect(row: NSInteger, clipRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawRow:clipRect:")
        ObjCRuntime.msgSend(null, ptr, sel, row, ObjCRuntime.ObjCStructArg(clipRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun highlightSelectionInClipRect(clipRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("highlightSelectionInClipRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(clipRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawGridInClipRect(clipRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawGridInClipRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(clipRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawBackgroundInClipRect(clipRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawBackgroundInClipRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(clipRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun viewAtColumn_row_makeIfNecessary(column: NSInteger, row: NSInteger, makeIfNecessary: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("viewAtColumn:row:makeIfNecessary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column, row, makeIfNecessary) as MemorySegment
    }
    
    fun rowViewAtRow_makeIfNecessary(row: NSInteger, makeIfNecessary: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("rowViewAtRow:makeIfNecessary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row, makeIfNecessary) as MemorySegment
    }
    
    fun rowForView(view: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("rowForView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, view) as NSInteger
    }
    
    fun columnForView(view: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("columnForView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, view) as NSInteger
    }
    
    fun makeViewWithIdentifier_owner(identifier: NSUserInterfaceItemIdentifier, owner: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeViewWithIdentifier:owner:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier, owner) as MemorySegment
    }
    
    fun enumerateAvailableRowViewsUsingBlock(handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateAvailableRowViewsUsingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, handler)
    }
    
    fun beginUpdates(): Unit {
        val sel = ObjCRuntime.sel("beginUpdates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun endUpdates(): Unit {
        val sel = ObjCRuntime.sel("endUpdates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun insertRowsAtIndexes_withAnimation(indexes: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("insertRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, animationOptions)
    }
    
    fun removeRowsAtIndexes_withAnimation(indexes: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("removeRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, animationOptions)
    }
    
    fun moveRowAtIndex_toIndex(oldIndex: NSInteger, newIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveRowAtIndex:toIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, oldIndex, newIndex)
    }
    
    fun hideRowsAtIndexes_withAnimation(indexes: MemorySegment, rowAnimation: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("hideRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, rowAnimation)
    }
    
    fun unhideRowsAtIndexes_withAnimation(indexes: MemorySegment, rowAnimation: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("unhideRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, rowAnimation)
    }
    
    fun registerNib_forIdentifier(nib: MemorySegment, identifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerNib:forIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, nib, identifier)
    }
    
    fun didAddRowView_forRow(rowView: MemorySegment, row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("didAddRowView:forRow:")
        ObjCRuntime.msgSend(null, ptr, sel, rowView, row)
    }
    
    fun didRemoveRowView_forRow(rowView: MemorySegment, row: NSInteger): Unit {
        val sel = ObjCRuntime.sel("didRemoveRowView:forRow:")
        ObjCRuntime.msgSend(null, ptr, sel, rowView, row)
    }
    
    // @property dataSource
    /** @return id<NSTableViewDataSource> */
    fun dataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("dataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDataSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSTableViewDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property headerView
    fun headerView(): MemorySegment {
        val sel = ObjCRuntime.sel("headerView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHeaderView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHeaderView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cornerView
    fun cornerView(): MemorySegment {
        val sel = ObjCRuntime.sel("cornerView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCornerView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCornerView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsColumnReordering
    fun allowsColumnReordering(): BOOL {
        val sel = ObjCRuntime.sel("allowsColumnReordering")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsColumnReordering(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsColumnReordering:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsColumnResizing
    fun allowsColumnResizing(): BOOL {
        val sel = ObjCRuntime.sel("allowsColumnResizing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsColumnResizing(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsColumnResizing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property columnAutoresizingStyle
    fun columnAutoresizingStyle(): NSTableViewColumnAutoresizingStyle {
        val sel = ObjCRuntime.sel("columnAutoresizingStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewColumnAutoresizingStyle
    }
    fun setColumnAutoresizingStyle(value: NSTableViewColumnAutoresizingStyle) {
        val sel = ObjCRuntime.sel("setColumnAutoresizingStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property gridStyleMask
    fun gridStyleMask(): NSTableViewGridLineStyle {
        val sel = ObjCRuntime.sel("gridStyleMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewGridLineStyle
    }
    fun setGridStyleMask(value: NSTableViewGridLineStyle) {
        val sel = ObjCRuntime.sel("setGridStyleMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property usesAlternatingRowBackgroundColors
    fun usesAlternatingRowBackgroundColors(): BOOL {
        val sel = ObjCRuntime.sel("usesAlternatingRowBackgroundColors")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesAlternatingRowBackgroundColors(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesAlternatingRowBackgroundColors:")
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
    
    // @property gridColor
    fun gridColor(): MemorySegment {
        val sel = ObjCRuntime.sel("gridColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGridColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGridColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowSizeStyle
    fun rowSizeStyle(): NSTableViewRowSizeStyle {
        val sel = ObjCRuntime.sel("rowSizeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewRowSizeStyle
    }
    fun setRowSizeStyle(value: NSTableViewRowSizeStyle) {
        val sel = ObjCRuntime.sel("setRowSizeStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property effectiveRowSizeStyle
    fun effectiveRowSizeStyle(): NSTableViewRowSizeStyle {
        val sel = ObjCRuntime.sel("effectiveRowSizeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewRowSizeStyle
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
    
    // @property tableColumns
    /** @return NSArray<NSTableColumn *> * */
    fun tableColumns(): MemorySegment {
        val sel = ObjCRuntime.sel("tableColumns")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfColumns
    fun numberOfColumns(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfRows
    fun numberOfRows(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property editedColumn
    fun editedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("editedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property editedRow
    fun editedRow(): NSInteger {
        val sel = ObjCRuntime.sel("editedRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
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
    
    // @property doubleAction
    fun doubleAction(): MemorySegment {
        val sel = ObjCRuntime.sel("doubleAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDoubleAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDoubleAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sortDescriptors
    /** @return NSArray<NSSortDescriptor *> * */
    fun sortDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("sortDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSortDescriptors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSortDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlightedTableColumn
    fun highlightedTableColumn(): MemorySegment {
        val sel = ObjCRuntime.sel("highlightedTableColumn")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHighlightedTableColumn(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHighlightedTableColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalMotionCanBeginDrag
    fun verticalMotionCanBeginDrag(): BOOL {
        val sel = ObjCRuntime.sel("verticalMotionCanBeginDrag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVerticalMotionCanBeginDrag(value: BOOL) {
        val sel = ObjCRuntime.sel("setVerticalMotionCanBeginDrag:")
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
    
    // @property allowsEmptySelection
    fun allowsEmptySelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsEmptySelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsEmptySelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsEmptySelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsColumnSelection
    fun allowsColumnSelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsColumnSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsColumnSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsColumnSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedColumnIndexes
    fun selectedColumnIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedColumnIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedRowIndexes
    fun selectedRowIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedRowIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedColumn
    fun selectedColumn(): NSInteger {
        val sel = ObjCRuntime.sel("selectedColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedRow
    fun selectedRow(): NSInteger {
        val sel = ObjCRuntime.sel("selectedRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfSelectedColumns
    fun numberOfSelectedColumns(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfSelectedColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfSelectedRows
    fun numberOfSelectedRows(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfSelectedRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
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
    
    // @property style
    fun style(): NSTableViewStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewStyle
    }
    fun setStyle(value: NSTableViewStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property effectiveStyle
    fun effectiveStyle(): NSTableViewStyle {
        val sel = ObjCRuntime.sel("effectiveStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewStyle
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
    
    // @property draggingDestinationFeedbackStyle
    fun draggingDestinationFeedbackStyle(): NSTableViewDraggingDestinationFeedbackStyle {
        val sel = ObjCRuntime.sel("draggingDestinationFeedbackStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewDraggingDestinationFeedbackStyle
    }
    fun setDraggingDestinationFeedbackStyle(value: NSTableViewDraggingDestinationFeedbackStyle) {
        val sel = ObjCRuntime.sel("setDraggingDestinationFeedbackStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosaveName
    fun autosaveName(): NSTableViewAutosaveName {
        val sel = ObjCRuntime.sel("autosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewAutosaveName
    }
    fun setAutosaveName(value: NSTableViewAutosaveName) {
        val sel = ObjCRuntime.sel("setAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosaveTableColumns
    fun autosaveTableColumns(): BOOL {
        val sel = ObjCRuntime.sel("autosaveTableColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutosaveTableColumns(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutosaveTableColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property floatsGroupRows
    fun floatsGroupRows(): BOOL {
        val sel = ObjCRuntime.sel("floatsGroupRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFloatsGroupRows(value: BOOL) {
        val sel = ObjCRuntime.sel("setFloatsGroupRows:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowActionsVisible
    fun rowActionsVisible(): BOOL {
        val sel = ObjCRuntime.sel("rowActionsVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRowActionsVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setRowActionsVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hiddenRowIndexes
    fun hiddenRowIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("hiddenRowIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property registeredNibsByIdentifier
    /** @return NSDictionary<NSUserInterfaceItemIdentifier,NSNib *> * */
    fun registeredNibsByIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("registeredNibsByIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property usesStaticContents
    fun usesStaticContents(): BOOL {
        val sel = ObjCRuntime.sel("usesStaticContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesStaticContents(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesStaticContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userInterfaceLayoutDirection
    fun userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    fun setUserInterfaceLayoutDirection(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesAutomaticRowHeights
    fun usesAutomaticRowHeights(): BOOL {
        val sel = ObjCRuntime.sel("usesAutomaticRowHeights")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesAutomaticRowHeights(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesAutomaticRowHeights:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSTableView ─────────────────────────────────────────

fun NSTableView.setDrawsGrid(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setDrawsGrid:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSTableView.drawsGrid(): BOOL {
    val sel = ObjCRuntime.sel("drawsGrid")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTableView.selectColumn_byExtendingSelection(column: NSInteger, extend: BOOL): Unit {
    val sel = ObjCRuntime.sel("selectColumn:byExtendingSelection:")
    ObjCRuntime.msgSend(null, ptr, sel, column, extend)
}

fun NSTableView.selectRow_byExtendingSelection(row: NSInteger, extend: BOOL): Unit {
    val sel = ObjCRuntime.sel("selectRow:byExtendingSelection:")
    ObjCRuntime.msgSend(null, ptr, sel, row, extend)
}

fun NSTableView.selectedColumnEnumerator(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedColumnEnumerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTableView.selectedRowEnumerator(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedRowEnumerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTableView.dragImageForRows_event_dragImageOffset(dragRows: MemorySegment, dragEvent: MemorySegment, dragImageOffset: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dragImageForRows:event:dragImageOffset:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dragRows, dragEvent, dragImageOffset) as MemorySegment
}

fun NSTableView.setAutoresizesAllColumnsToFit(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutoresizesAllColumnsToFit:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSTableView.autoresizesAllColumnsToFit(): BOOL {
    val sel = ObjCRuntime.sel("autoresizesAllColumnsToFit")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTableView.columnsInRect(rect: NSRect): NSRange {
    val sel = ObjCRuntime.sel("columnsInRect:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, rect) as NSRange
}

fun NSTableView.preparedCellAtColumn_row(column: NSInteger, row: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("preparedCellAtColumn:row:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, column, row) as MemorySegment
}

fun NSTableView.textShouldBeginEditing(textObject: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("textShouldBeginEditing:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
}

fun NSTableView.textShouldEndEditing(textObject: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("textShouldEndEditing:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
}

fun NSTableView.textDidBeginEditing(notification: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("textDidBeginEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, notification)
}

fun NSTableView.textDidEndEditing(notification: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("textDidEndEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, notification)
}

fun NSTableView.textDidChange(notification: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("textDidChange:")
    ObjCRuntime.msgSend(null, ptr, sel, notification)
}

fun NSTableView.shouldFocusCell_atColumn_row(cell: MemorySegment, column: NSInteger, row: NSInteger): BOOL {
    val sel = ObjCRuntime.sel("shouldFocusCell:atColumn:row:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, cell, column, row) as BOOL
}

fun NSTableView.focusedColumn(): NSInteger {
    val sel = ObjCRuntime.sel("focusedColumn")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSTableView.setFocusedColumn(focusedColumn: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setFocusedColumn:")
    ObjCRuntime.msgSend(null, ptr, sel, focusedColumn)
}

fun NSTableView.performClickOnCellAtColumn_row(column: NSInteger, row: NSInteger): Unit {
    val sel = ObjCRuntime.sel("performClickOnCellAtColumn:row:")
    ObjCRuntime.msgSend(null, ptr, sel, column, row)
}

