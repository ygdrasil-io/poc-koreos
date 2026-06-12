package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableURLRequest
 * Superclass: NSURLRequest
 */
open class NSMutableURLRequest(ptr: MemorySegment) : NSURLRequest(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableURLRequest") }
        
    }
    
    // @property URL
    override fun `URL`(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cachePolicy
    override fun `cachePolicy`(): NSURLRequestCachePolicy {
        val sel = ObjCRuntime.sel("cachePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestCachePolicy
    }
    fun setCachePolicy(value: NSURLRequestCachePolicy) {
        val sel = ObjCRuntime.sel("setCachePolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeoutInterval
    override fun `timeoutInterval`(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeoutInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setTimeoutInterval(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTimeoutInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mainDocumentURL
    override fun `mainDocumentURL`(): MemorySegment {
        val sel = ObjCRuntime.sel("mainDocumentURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMainDocumentURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMainDocumentURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property networkServiceType
    override fun `networkServiceType`(): NSURLRequestNetworkServiceType {
        val sel = ObjCRuntime.sel("networkServiceType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestNetworkServiceType
    }
    fun setNetworkServiceType(value: NSURLRequestNetworkServiceType) {
        val sel = ObjCRuntime.sel("setNetworkServiceType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsCellularAccess
    override fun `allowsCellularAccess`(): BOOL {
        val sel = ObjCRuntime.sel("allowsCellularAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsCellularAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsCellularAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsExpensiveNetworkAccess
    override fun `allowsExpensiveNetworkAccess`(): BOOL {
        val sel = ObjCRuntime.sel("allowsExpensiveNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsExpensiveNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsExpensiveNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsConstrainedNetworkAccess
    override fun `allowsConstrainedNetworkAccess`(): BOOL {
        val sel = ObjCRuntime.sel("allowsConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsConstrainedNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsConstrainedNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsUltraConstrainedNetworkAccess
    override fun `allowsUltraConstrainedNetworkAccess`(): BOOL {
        val sel = ObjCRuntime.sel("allowsUltraConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsUltraConstrainedNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsUltraConstrainedNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property assumesHTTP3Capable
    override fun `assumesHTTP3Capable`(): BOOL {
        val sel = ObjCRuntime.sel("assumesHTTP3Capable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAssumesHTTP3Capable(value: BOOL) {
        val sel = ObjCRuntime.sel("setAssumesHTTP3Capable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attribution
    override fun `attribution`(): NSURLRequestAttribution {
        val sel = ObjCRuntime.sel("attribution")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestAttribution
    }
    fun setAttribution(value: NSURLRequestAttribution) {
        val sel = ObjCRuntime.sel("setAttribution:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property requiresDNSSECValidation
    override fun `requiresDNSSECValidation`(): BOOL {
        val sel = ObjCRuntime.sel("requiresDNSSECValidation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRequiresDNSSECValidation(value: BOOL) {
        val sel = ObjCRuntime.sel("setRequiresDNSSECValidation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsPersistentDNS
    override fun `allowsPersistentDNS`(): BOOL {
        val sel = ObjCRuntime.sel("allowsPersistentDNS")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsPersistentDNS(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsPersistentDNS:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cookiePartitionIdentifier
    override fun `cookiePartitionIdentifier`(): MemorySegment {
        val sel = ObjCRuntime.sel("cookiePartitionIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCookiePartitionIdentifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCookiePartitionIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    override fun `cookiePartitionIdentifierAsString`(): String = ObjCRuntime.toJavaString(cookiePartitionIdentifier())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCookiePartitionIdentifier(value: String) = setCookiePartitionIdentifier(ObjCRuntime.newNSString(Arena.global(), value))
    
}

// ── Category: NSMutableHTTPURLRequest on NSMutableURLRequest ─────────────────────────────────────────

fun NSMutableURLRequest.setValue_forHTTPHeaderField(value: MemorySegment, field: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forHTTPHeaderField:")
    ObjCRuntime.msgSend(null, ptr, sel, value, field)
}

fun NSMutableURLRequest.addValue_forHTTPHeaderField(value: MemorySegment, field: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addValue:forHTTPHeaderField:")
    ObjCRuntime.msgSend(null, ptr, sel, value, field)
}

fun NSMutableURLRequest.HTTPMethod(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPMethod")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMutableURLRequest.setHTTPMethod(HTTPMethod: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setHTTPMethod:")
    ObjCRuntime.msgSend(null, ptr, sel, HTTPMethod)
}

/** @return NSDictionary<NSString *,NSString *> * */
fun NSMutableURLRequest.allHTTPHeaderFields(): MemorySegment {
    val sel = ObjCRuntime.sel("allHTTPHeaderFields")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMutableURLRequest.setAllHTTPHeaderFields(allHTTPHeaderFields: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAllHTTPHeaderFields:")
    ObjCRuntime.msgSend(null, ptr, sel, allHTTPHeaderFields)
}

fun NSMutableURLRequest.HTTPBody(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPBody")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMutableURLRequest.setHTTPBody(HTTPBody: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setHTTPBody:")
    ObjCRuntime.msgSend(null, ptr, sel, HTTPBody)
}

fun NSMutableURLRequest.HTTPBodyStream(): MemorySegment {
    val sel = ObjCRuntime.sel("HTTPBodyStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMutableURLRequest.setHTTPBodyStream(HTTPBodyStream: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setHTTPBodyStream:")
    ObjCRuntime.msgSend(null, ptr, sel, HTTPBodyStream)
}

fun NSMutableURLRequest.HTTPShouldHandleCookies(): BOOL {
    val sel = ObjCRuntime.sel("HTTPShouldHandleCookies")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSMutableURLRequest.setHTTPShouldHandleCookies(HTTPShouldHandleCookies: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHTTPShouldHandleCookies:")
    ObjCRuntime.msgSend(null, ptr, sel, HTTPShouldHandleCookies)
}

fun NSMutableURLRequest.HTTPShouldUsePipelining(): BOOL {
    val sel = ObjCRuntime.sel("HTTPShouldUsePipelining")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSMutableURLRequest.setHTTPShouldUsePipelining(HTTPShouldUsePipelining: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHTTPShouldUsePipelining:")
    ObjCRuntime.msgSend(null, ptr, sel, HTTPShouldUsePipelining)
}

// @property HTTPMethod
/** @return NSDictionary<NSString *,NSString *> * */