/**
 * Kotlin/JVM wrapper for Objective-C class: NSError
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSError(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSError") }
        
        fun errorWithDomain_code_userInfo(domain: NSErrorDomain, code: NSInteger, dict: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("errorWithDomain:code:userInfo:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, domain, code, dict) as MemorySegment
        }
        
        fun setUserInfoValueProviderForDomain_provider(errorDomain: NSErrorDomain, provider: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setUserInfoValueProviderForDomain:provider:")
            ObjCRuntime.msgSend(null, _class, sel, errorDomain, provider)
        }
        
        fun userInfoValueProviderForDomain(errorDomain: NSErrorDomain): MemorySegment {
            val sel = ObjCRuntime.sel("userInfoValueProviderForDomain:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, errorDomain) as MemorySegment
        }
        
    }
    
    fun initWithDomain_code_userInfo(domain: NSErrorDomain, code: NSInteger, dict: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDomain:code:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domain, code, dict) as MemorySegment
    }
    
    // @property domain
    fun domain(): NSErrorDomain {
        val sel = ObjCRuntime.sel("domain")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSErrorDomain
    }
    
    // @property code
    fun code(): NSInteger {
        val sel = ObjCRuntime.sel("code")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property userInfo
    /** @return NSDictionary<NSErrorUserInfoKey,id> * */
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedDescription
    fun localizedDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedDescriptionAsString(): String = ObjCRuntime.toJavaString(localizedDescription())
    
    // @property localizedFailureReason
    fun localizedFailureReason(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedFailureReason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedFailureReasonAsString(): String = ObjCRuntime.toJavaString(localizedFailureReason())
    
    // @property localizedRecoverySuggestion
    fun localizedRecoverySuggestion(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedRecoverySuggestion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedRecoverySuggestionAsString(): String = ObjCRuntime.toJavaString(localizedRecoverySuggestion())
    
    // @property localizedRecoveryOptions
    /** @return NSArray<NSString *> * */
    fun localizedRecoveryOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedRecoveryOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property recoveryAttempter
    fun recoveryAttempter(): MemorySegment {
        val sel = ObjCRuntime.sel("recoveryAttempter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property helpAnchor
    fun helpAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("helpAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun helpAnchorAsString(): String = ObjCRuntime.toJavaString(helpAnchor())
    
    // @property underlyingErrors
    /** @return NSArray<NSError *> * */
    fun underlyingErrors(): MemorySegment {
        val sel = ObjCRuntime.sel("underlyingErrors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

