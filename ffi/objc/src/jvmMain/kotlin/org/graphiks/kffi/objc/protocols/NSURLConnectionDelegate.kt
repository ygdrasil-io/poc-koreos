/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLConnectionDelegate
 * Inherits protocols: NSObject
 */
interface NSURLConnectionDelegate : NSObject {
    // @optional
    fun connection_didFailWithError(connection: MemorySegment, error: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connection:didFailWithError:' not implemented")
    
    // @optional
    fun connectionShouldUseCredentialStorage(connection: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'connectionShouldUseCredentialStorage:' not implemented")
    
    // @optional
    fun connection_willSendRequestForAuthenticationChallenge(connection: MemorySegment, challenge: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connection:willSendRequestForAuthenticationChallenge:' not implemented")
    
    // @optional
    fun connection_canAuthenticateAgainstProtectionSpace(connection: MemorySegment, protectionSpace: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'connection:canAuthenticateAgainstProtectionSpace:' not implemented")
    
    // @optional
    fun connection_didReceiveAuthenticationChallenge(connection: MemorySegment, challenge: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connection:didReceiveAuthenticationChallenge:' not implemented")
    
    // @optional
    fun connection_didCancelAuthenticationChallenge(connection: MemorySegment, challenge: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'connection:didCancelAuthenticationChallenge:' not implemented")
    
}

