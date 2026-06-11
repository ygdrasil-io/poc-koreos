/**
 * Kotlin/JVM interface for Objective-C protocol: NSServicesMenuRequestor
 * Inherits protocols: NSObject
 */
interface NSServicesMenuRequestor : NSObject {
    // @optional
    fun writeSelectionToPasteboard_types(pboard: MemorySegment, types: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'writeSelectionToPasteboard:types:' not implemented")
    
    // @optional
    fun readSelectionFromPasteboard(pboard: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'readSelectionFromPasteboard:' not implemented")
    
}

