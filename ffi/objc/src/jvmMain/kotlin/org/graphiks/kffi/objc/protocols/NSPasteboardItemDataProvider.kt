/**
 * Kotlin/JVM interface for Objective-C protocol: NSPasteboardItemDataProvider
 * Inherits protocols: NSObject
 */
interface NSPasteboardItemDataProvider : NSObject {
    fun pasteboard_item_provideDataForType(pasteboard: MemorySegment, item: MemorySegment, type: NSPasteboardType)
    
    // @optional
    fun pasteboardFinishedWithDataProvider(pasteboard: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pasteboardFinishedWithDataProvider:' not implemented")
    
}

