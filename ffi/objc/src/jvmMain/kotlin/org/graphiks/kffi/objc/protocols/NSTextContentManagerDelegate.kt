/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextContentManagerDelegate
 * Inherits protocols: NSObject
 */
interface NSTextContentManagerDelegate : NSObject {
    // @optional
    fun textContentManager_textElementAtLocation(textContentManager: MemorySegment, location: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'textContentManager:textElementAtLocation:' not implemented")
    
    // @optional
    fun textContentManager_shouldEnumerateTextElement_options(textContentManager: MemorySegment, textElement: MemorySegment, options: NSTextContentManagerEnumerationOptions): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'textContentManager:shouldEnumerateTextElement:options:' not implemented")
    
}

