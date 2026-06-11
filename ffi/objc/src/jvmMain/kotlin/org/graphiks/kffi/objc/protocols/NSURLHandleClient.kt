/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLHandleClient
 */
interface NSURLHandleClient {
    fun URLHandle_resourceDataDidBecomeAvailable(sender: MemorySegment, newBytes: MemorySegment)
    
    fun URLHandleResourceDidBeginLoading(sender: MemorySegment)
    
    fun URLHandleResourceDidFinishLoading(sender: MemorySegment)
    
    fun URLHandleResourceDidCancelLoading(sender: MemorySegment)
    
    fun URLHandle_resourceDidFailLoadingWithReason(sender: MemorySegment, reason: MemorySegment)
    
}

