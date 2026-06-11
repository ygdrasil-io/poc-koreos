/**
 * Kotlin/JVM interface for Objective-C protocol: NSDatePickerCellDelegate
 * Inherits protocols: NSObject
 */
interface NSDatePickerCellDelegate : NSObject {
    // @optional
    fun datePickerCell_validateProposedDateValue_timeInterval(datePickerCell: MemorySegment, proposedDateValue: MemorySegment, proposedTimeInterval: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'datePickerCell:validateProposedDateValue:timeInterval:' not implemented")
    
}

