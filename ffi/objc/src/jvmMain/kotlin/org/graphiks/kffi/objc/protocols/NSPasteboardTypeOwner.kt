/**
 * Kotlin/JVM interface for Objective-C protocol: NSPasteboardTypeOwner
 * Inherits protocols: NSObject
 */
interface NSPasteboardTypeOwner : NSObject {
    fun pasteboard_provideDataForType(sender: MemorySegment, type: NSPasteboardType)
    
    // @optional
    fun pasteboardChangedOwner(sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pasteboardChangedOwner:' not implemented")
    
}

