/**
 * Kotlin/JVM interface for Objective-C protocol: NSPasteboardReading
 * Inherits protocols: NSObject
 */
interface NSPasteboardReading : NSObject {
    /** @return NSArray<NSPasteboardType> * */
    fun readableTypesForPasteboard(pasteboard: MemorySegment): MemorySegment
    
    // @optional
    fun readingOptionsForType_pasteboard(type: NSPasteboardType, pasteboard: MemorySegment): NSPasteboardReadingOptions =
        throw UnsupportedOperationException("Optional ObjC method 'readingOptionsForType:pasteboard:' not implemented")
    
    // @optional
    fun initWithPasteboardPropertyList_ofType(propertyList: MemorySegment, type: NSPasteboardType): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'initWithPasteboardPropertyList:ofType:' not implemented")
    
}

