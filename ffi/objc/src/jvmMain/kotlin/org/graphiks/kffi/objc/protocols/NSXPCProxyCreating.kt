/**
 * Kotlin/JVM interface for Objective-C protocol: NSXPCProxyCreating
 */
interface NSXPCProxyCreating {
    fun remoteObjectProxy(): MemorySegment
    
    fun remoteObjectProxyWithErrorHandler(handler: MemorySegment): MemorySegment
    
    // @optional
    fun synchronousRemoteObjectProxyWithErrorHandler(handler: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'synchronousRemoteObjectProxyWithErrorHandler:' not implemented")
    
}

