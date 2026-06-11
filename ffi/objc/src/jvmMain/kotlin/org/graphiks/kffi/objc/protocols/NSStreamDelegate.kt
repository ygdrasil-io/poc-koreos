/**
 * Kotlin/JVM interface for Objective-C protocol: NSStreamDelegate
 * Inherits protocols: NSObject
 */
interface NSStreamDelegate : NSObject {
    // @optional
    fun stream_handleEvent(aStream: MemorySegment, eventCode: NSStreamEvent): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'stream:handleEvent:' not implemented")
    
}

