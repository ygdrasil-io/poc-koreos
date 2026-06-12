package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSURLAuthenticationChallengeSender
 * Inherits protocols: NSObject
 */
interface NSURLAuthenticationChallengeSender : NSObject {
    fun useCredential_forAuthenticationChallenge(credential: MemorySegment, challenge: MemorySegment)
    
    fun continueWithoutCredentialForAuthenticationChallenge(challenge: MemorySegment)
    
    fun cancelAuthenticationChallenge(challenge: MemorySegment)
    
    // @optional
    fun performDefaultHandlingForAuthenticationChallenge(challenge: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'performDefaultHandlingForAuthenticationChallenge:' not implemented")
    
    // @optional
    fun rejectProtectionSpaceAndContinueWithChallenge(challenge: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'rejectProtectionSpaceAndContinueWithChallenge:' not implemented")
    
}

