/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLAuthenticationChallenge
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSURLAuthenticationChallenge(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLAuthenticationChallenge") }
        
    }
    
    fun initWithProtectionSpace_proposedCredential_previousFailureCount_failureResponse_error_sender(space: MemorySegment, credential: MemorySegment, previousFailureCount: NSInteger, response: MemorySegment, error: MemorySegment, sender: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithProtectionSpace:proposedCredential:previousFailureCount:failureResponse:error:sender:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, space, credential, previousFailureCount, response, error, sender) as MemorySegment
    }
    
    fun initWithAuthenticationChallenge_sender(challenge: MemorySegment, sender: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithAuthenticationChallenge:sender:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, challenge, sender) as MemorySegment
    }
    
    // @property protectionSpace
    fun protectionSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("protectionSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property proposedCredential
    fun proposedCredential(): MemorySegment {
        val sel = ObjCRuntime.sel("proposedCredential")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property previousFailureCount
    fun previousFailureCount(): NSInteger {
        val sel = ObjCRuntime.sel("previousFailureCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property failureResponse
    fun failureResponse(): MemorySegment {
        val sel = ObjCRuntime.sel("failureResponse")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property error
    fun error(): MemorySegment {
        val sel = ObjCRuntime.sel("error")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sender
    /** @return id<NSURLAuthenticationChallengeSender> */
    fun sender(): MemorySegment {
        val sel = ObjCRuntime.sel("sender")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

