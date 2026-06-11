/**
 * Kotlin/JVM interface for Objective-C protocol: NSXPCListenerDelegate
 * Inherits protocols: NSObject
 */
interface NSXPCListenerDelegate : NSObject {
    // @optional
    fun listener_shouldAcceptNewConnection(listener: MemorySegment, newConnection: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'listener:shouldAcceptNewConnection:' not implemented")
    
}

