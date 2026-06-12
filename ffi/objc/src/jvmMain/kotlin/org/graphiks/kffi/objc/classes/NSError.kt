package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSError
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSError(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSError") }
        
        open fun errorWithDomain_code_userInfo(domain: NSErrorDomain, code: NSInteger, dict: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("errorWithDomain:code:userInfo:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, domain, code, dict) as MemorySegment
        }
        
        open fun setUserInfoValueProviderForDomain_provider(errorDomain: NSErrorDomain, provider: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setUserInfoValueProviderForDomain:provider:")
            ObjCRuntime.msgSend(null, _class, sel, errorDomain, provider)
        }
        
        open fun userInfoValueProviderForDomain(errorDomain: NSErrorDomain): MemorySegment {
            val sel = ObjCRuntime.sel("userInfoValueProviderForDomain:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, errorDomain) as MemorySegment
        }
        
    }
    
    open fun initWithDomain_code_userInfo(domain: NSErrorDomain, code: NSInteger, dict: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDomain:code:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domain, code, dict) as MemorySegment
    }
    
    // @property domain
    open fun domain(): NSErrorDomain {
        val sel = ObjCRuntime.sel("domain")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSErrorDomain
    }
    
    // @property code
    open fun code(): NSInteger {
        val sel = ObjCRuntime.sel("code")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property userInfo
    /** @return NSDictionary<NSErrorUserInfoKey,id> * */
    open fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedDescription
    open fun localizedDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedDescriptionAsString(): String = ObjCRuntime.toJavaString(localizedDescription())
    
    // @property localizedFailureReason
    open fun localizedFailureReason(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedFailureReason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedFailureReasonAsString(): String = ObjCRuntime.toJavaString(localizedFailureReason())
    
    // @property localizedRecoverySuggestion
    open fun localizedRecoverySuggestion(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedRecoverySuggestion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedRecoverySuggestionAsString(): String = ObjCRuntime.toJavaString(localizedRecoverySuggestion())
    
    // @property localizedRecoveryOptions
    /** @return NSArray<NSString *> * */
    open fun localizedRecoveryOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedRecoveryOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property recoveryAttempter
    open fun recoveryAttempter(): MemorySegment {
        val sel = ObjCRuntime.sel("recoveryAttempter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property helpAnchor
    open fun helpAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("helpAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun helpAnchorAsString(): String = ObjCRuntime.toJavaString(helpAnchor())
    
    // @property underlyingErrors
    /** @return NSArray<NSError *> * */
    open fun underlyingErrors(): MemorySegment {
        val sel = ObjCRuntime.sel("underlyingErrors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

