/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLProtocolClient
 * Inherits protocols: NSObject
 */
interface NSURLProtocolClient : NSObject {
    fun URLProtocol_wasRedirectedToRequest_redirectResponse(protocol: MemorySegment, request: MemorySegment, redirectResponse: MemorySegment)
    
    fun URLProtocol_cachedResponseIsValid(protocol: MemorySegment, cachedResponse: MemorySegment)
    
    fun URLProtocol_didReceiveResponse_cacheStoragePolicy(protocol: MemorySegment, response: MemorySegment, policy: NSURLCacheStoragePolicy)
    
    fun URLProtocol_didLoadData(protocol: MemorySegment, `data`: MemorySegment)
    
    fun URLProtocolDidFinishLoading(protocol: MemorySegment)
    
    fun URLProtocol_didFailWithError(protocol: MemorySegment, error: MemorySegment)
    
    fun URLProtocol_didReceiveAuthenticationChallenge(protocol: MemorySegment, challenge: MemorySegment)
    
    fun URLProtocol_didCancelAuthenticationChallenge(protocol: MemorySegment, challenge: MemorySegment)
    
}

