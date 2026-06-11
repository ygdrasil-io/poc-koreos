/**
 * Kotlin/JVM interface for Objective-C protocol: NSInputServerMouseTracker
 */
interface NSInputServerMouseTracker {
    fun mouseDownOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment): BOOL
    
    fun mouseDraggedOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment): BOOL
    
    fun mouseUpOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment)
    
}

