/**
 * Kotlin/JVM interface for Objective-C protocol: NSComboBoxCellDataSource
 * Inherits protocols: NSObject
 */
interface NSComboBoxCellDataSource : NSObject {
    // @optional
    fun numberOfItemsInComboBoxCell(comboBoxCell: MemorySegment): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'numberOfItemsInComboBoxCell:' not implemented")
    
    // @optional
    fun comboBoxCell_objectValueForItemAtIndex(comboBoxCell: MemorySegment, index: NSInteger): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'comboBoxCell:objectValueForItemAtIndex:' not implemented")
    
    // @optional
    fun comboBoxCell_indexOfItemWithStringValue(comboBoxCell: MemorySegment, string: MemorySegment): NSUInteger =
        throw UnsupportedOperationException("Optional ObjC method 'comboBoxCell:indexOfItemWithStringValue:' not implemented")
    
    // @optional
    fun comboBoxCell_completedString(comboBoxCell: MemorySegment, uncompletedString: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'comboBoxCell:completedString:' not implemented")
    
}

