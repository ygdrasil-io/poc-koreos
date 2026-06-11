/**
 * Kotlin/JVM interface for Objective-C protocol: NSTouchBarDelegate
 * Inherits protocols: NSObject
 */
interface NSTouchBarDelegate : NSObject {
    // @optional
    fun touchBar_makeItemForIdentifier(touchBar: MemorySegment, identifier: NSTouchBarItemIdentifier): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'touchBar:makeItemForIdentifier:' not implemented")
    
}

