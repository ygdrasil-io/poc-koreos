/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLSessionDelegate
 * Inherits protocols: NSObject
 */
interface NSURLSessionDelegate : NSObject {
    // @optional
    fun URLSession_didBecomeInvalidWithError(session: MemorySegment, error: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'URLSession:didBecomeInvalidWithError:' not implemented")
    
    // @optional
    fun URLSession_didReceiveChallenge_completionHandler(session: MemorySegment, challenge: MemorySegment, completionHandler: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'URLSession:didReceiveChallenge:completionHandler:' not implemented")
    
    // @optional
    fun URLSessionDidFinishEventsForBackgroundURLSession(session: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'URLSessionDidFinishEventsForBackgroundURLSession:' not implemented")
    
}

