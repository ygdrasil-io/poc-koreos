/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextStorageDelegate
 * Inherits protocols: NSObject
 */
interface NSTextStorageDelegate : NSObject {
    // @optional
    fun textStorage_willProcessEditing_range_changeInLength(textStorage: MemorySegment, editedMask: NSTextStorageEditActions, editedRange: NSRange, delta: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'textStorage:willProcessEditing:range:changeInLength:' not implemented")
    
    // @optional
    fun textStorage_didProcessEditing_range_changeInLength(textStorage: MemorySegment, editedMask: NSTextStorageEditActions, editedRange: NSRange, delta: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'textStorage:didProcessEditing:range:changeInLength:' not implemented")
    
}

