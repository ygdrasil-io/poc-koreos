package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLRequest
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying, NSMutableCopying
 */
open class NSURLRequest(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLRequest") }
        
        open fun requestWithURL(URL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("requestWithURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URL) as MemorySegment
        }
        
        open fun requestWithURL_cachePolicy_timeoutInterval(URL: MemorySegment, cachePolicy: NSURLRequestCachePolicy, timeoutInterval: NSTimeInterval): MemorySegment {
            val sel = ObjCRuntime.sel("requestWithURL:cachePolicy:timeoutInterval:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URL, cachePolicy, timeoutInterval) as MemorySegment
        }
        
        open fun supportsSecureCoding(): BOOL {
            val sel = ObjCRuntime.sel("supportsSecureCoding")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun initWithURL(URL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URL) as MemorySegment
    }
    
    open fun initWithURL_cachePolicy_timeoutInterval(URL: MemorySegment, cachePolicy: NSURLRequestCachePolicy, timeoutInterval: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:cachePolicy:timeoutInterval:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URL, cachePolicy, timeoutInterval) as MemorySegment
    }
    
    // @property supportsSecureCoding
    }
    
    // @property URL
    open fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cachePolicy
    open fun cachePolicy(): NSURLRequestCachePolicy {
        val sel = ObjCRuntime.sel("cachePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestCachePolicy
    }
    
    // @property timeoutInterval
    open fun timeoutInterval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeoutInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property mainDocumentURL
    open fun mainDocumentURL(): MemorySegment {
        val sel = ObjCRuntime.sel("mainDocumentURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property networkServiceType
    open fun networkServiceType(): NSURLRequestNetworkServiceType {
        val sel = ObjCRuntime.sel("networkServiceType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestNetworkServiceType
    }
    
    // @property allowsCellularAccess
    open fun allowsCellularAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsCellularAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsExpensiveNetworkAccess
    open fun allowsExpensiveNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsExpensiveNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsConstrainedNetworkAccess
    open fun allowsConstrainedNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsUltraConstrainedNetworkAccess
    open fun allowsUltraConstrainedNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsUltraConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property assumesHTTP3Capable
    open fun assumesHTTP3Capable(): BOOL {
        val sel = ObjCRuntime.sel("assumesHTTP3Capable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property attribution
    open fun attribution(): NSURLRequestAttribution {
        val sel = ObjCRuntime.sel("attribution")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestAttribution
    }
    
    // @property requiresDNSSECValidation
    open fun requiresDNSSECValidation(): BOOL {
        val sel = ObjCRuntime.sel("requiresDNSSECValidation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsPersistentDNS
    open fun allowsPersistentDNS(): BOOL {
        val sel = ObjCRuntime.sel("allowsPersistentDNS")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property cookiePartitionIdentifier
    open fun cookiePartitionIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("cookiePartitionIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun cookiePartitionIdentifierAsString(): String = ObjCRuntime.toJavaString(cookiePartitionIdentifier())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

// ── Category: NSHTTPURLRequest on NSURLRequest ─────────────────────────────────────────

fun NSURLRequest.valueForHTTPHeaderField(field: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForHTTPHeaderField:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, field) as MemorySegment
}

fun NSURLRequest.HTTPMethod(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPMethod")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSDictionary<NSString *,NSString *> * */
fun NSURLRequest.allHTTPHeaderFields(): MemorySegment {
    val sel = ObjCRuntime.sel("allHTTPHeaderFields")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLRequest.HTTPBody(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPBody")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLRequest.HTTPBodyStream(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPBodyStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLRequest.HTTPShouldHandleCookies(): BOOL {
    val sel = ObjCRuntime.sel("HTTPShouldHandleCookies")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSURLRequest.HTTPShouldUsePipelining(): BOOL {
    val sel = ObjCRuntime.sel("HTTPShouldUsePipelining")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property HTTPMethod
    val sel = ObjCRuntime.sel("HTTPMethod")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property allHTTPHeaderFields
/** @return NSDictionary<NSString *,NSString *> * */
    val sel = ObjCRuntime.sel("allHTTPHeaderFields")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property HTTPBody
    val sel = ObjCRuntime.sel("HTTPBody")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property HTTPBodyStream
    val sel = ObjCRuntime.sel("HTTPBodyStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property HTTPShouldHandleCookies
    val sel = ObjCRuntime.sel("HTTPShouldHandleCookies")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property HTTPShouldUsePipelining
    val sel = ObjCRuntime.sel("HTTPShouldUsePipelining")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

