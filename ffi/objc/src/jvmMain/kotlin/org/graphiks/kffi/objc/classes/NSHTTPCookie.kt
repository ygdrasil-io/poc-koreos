/**
 * Kotlin/JVM wrapper for Objective-C class: NSHTTPCookie
 * Superclass: NSObject
 */
open class NSHTTPCookie(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHTTPCookie") }
        
        fun cookieWithProperties(properties: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("cookieWithProperties:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, properties) as MemorySegment
        }
        
        /** @return NSDictionary<NSString *,NSString *> * */
        fun requestHeaderFieldsWithCookies(cookies: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("requestHeaderFieldsWithCookies:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cookies) as MemorySegment
        }
        
        /** @return NSArray<NSHTTPCookie *> * */
        fun cookiesWithResponseHeaderFields_forURL(headerFields: MemorySegment, URL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("cookiesWithResponseHeaderFields:forURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, headerFields, URL) as MemorySegment
        }
        
    }
    
    fun initWithProperties(properties: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithProperties:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, properties) as MemorySegment
    }
    
    // @property properties
    /** @return NSDictionary<NSHTTPCookiePropertyKey,id> * */
    fun properties(): MemorySegment {
        val sel = ObjCRuntime.sel("properties")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property version
    fun version(): NSUInteger {
        val sel = ObjCRuntime.sel("version")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property value
    fun value(): MemorySegment {
        val sel = ObjCRuntime.sel("value")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun valueAsString(): String = ObjCRuntime.toJavaString(value())
    
    // @property expiresDate
    fun expiresDate(): MemorySegment {
        val sel = ObjCRuntime.sel("expiresDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sessionOnly
    fun isSessionOnly(): BOOL {
        val sel = ObjCRuntime.sel("isSessionOnly")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property domain
    fun domain(): MemorySegment {
        val sel = ObjCRuntime.sel("domain")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun domainAsString(): String = ObjCRuntime.toJavaString(domain())
    
    // @property path
    fun path(): MemorySegment {
        val sel = ObjCRuntime.sel("path")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathAsString(): String = ObjCRuntime.toJavaString(path())
    
    // @property secure
    fun isSecure(): BOOL {
        val sel = ObjCRuntime.sel("isSecure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property HTTPOnly
    fun isHTTPOnly(): BOOL {
        val sel = ObjCRuntime.sel("isHTTPOnly")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property comment
    fun comment(): MemorySegment {
        val sel = ObjCRuntime.sel("comment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun commentAsString(): String = ObjCRuntime.toJavaString(comment())
    
    // @property commentURL
    fun commentURL(): MemorySegment {
        val sel = ObjCRuntime.sel("commentURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property portList
    /** @return NSArray<NSNumber *> * */
    fun portList(): MemorySegment {
        val sel = ObjCRuntime.sel("portList")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sameSitePolicy
    fun sameSitePolicy(): NSHTTPCookieStringPolicy {
        val sel = ObjCRuntime.sel("sameSitePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSHTTPCookieStringPolicy
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _cookiePrivate: MemorySegment
}

