package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionTaskTransactionMetrics
 * Superclass: NSObject
 */
open class NSURLSessionTaskTransactionMetrics(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionTaskTransactionMetrics") }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property request
    open fun request(): MemorySegment {
        val sel = ObjCRuntime.sel("request")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property response
    open fun response(): MemorySegment {
        val sel = ObjCRuntime.sel("response")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fetchStartDate
    open fun fetchStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("fetchStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property domainLookupStartDate
    open fun domainLookupStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("domainLookupStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property domainLookupEndDate
    open fun domainLookupEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("domainLookupEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property connectStartDate
    open fun connectStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("connectStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secureConnectionStartDate
    open fun secureConnectionStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("secureConnectionStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secureConnectionEndDate
    open fun secureConnectionEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("secureConnectionEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property connectEndDate
    open fun connectEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("connectEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestStartDate
    open fun requestStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("requestStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestEndDate
    open fun requestEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("requestEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property responseStartDate
    open fun responseStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("responseStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property responseEndDate
    open fun responseEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("responseEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property networkProtocolName
    open fun networkProtocolName(): MemorySegment {
        val sel = ObjCRuntime.sel("networkProtocolName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun networkProtocolNameAsString(): String = ObjCRuntime.toJavaString(networkProtocolName())
    
    // @property proxyConnection
    open fun isProxyConnection(): BOOL {
        val sel = ObjCRuntime.sel("isProxyConnection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property reusedConnection
    open fun isReusedConnection(): BOOL {
        val sel = ObjCRuntime.sel("isReusedConnection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resourceFetchType
    open fun resourceFetchType(): NSURLSessionTaskMetricsResourceFetchType {
        val sel = ObjCRuntime.sel("resourceFetchType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionTaskMetricsResourceFetchType
    }
    
    // @property countOfRequestHeaderBytesSent
    open fun countOfRequestHeaderBytesSent(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestHeaderBytesSent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfRequestBodyBytesSent
    open fun countOfRequestBodyBytesSent(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestBodyBytesSent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfRequestBodyBytesBeforeEncoding
    open fun countOfRequestBodyBytesBeforeEncoding(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestBodyBytesBeforeEncoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseHeaderBytesReceived
    open fun countOfResponseHeaderBytesReceived(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseHeaderBytesReceived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseBodyBytesReceived
    open fun countOfResponseBodyBytesReceived(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseBodyBytesReceived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseBodyBytesAfterDecoding
    open fun countOfResponseBodyBytesAfterDecoding(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseBodyBytesAfterDecoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property localAddress
    open fun localAddress(): MemorySegment {
        val sel = ObjCRuntime.sel("localAddress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localAddressAsString(): String = ObjCRuntime.toJavaString(localAddress())
    
    // @property localPort
    open fun localPort(): MemorySegment {
        val sel = ObjCRuntime.sel("localPort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property remoteAddress
    open fun remoteAddress(): MemorySegment {
        val sel = ObjCRuntime.sel("remoteAddress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun remoteAddressAsString(): String = ObjCRuntime.toJavaString(remoteAddress())
    
    // @property remotePort
    open fun remotePort(): MemorySegment {
        val sel = ObjCRuntime.sel("remotePort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property negotiatedTLSProtocolVersion
    open fun negotiatedTLSProtocolVersion(): MemorySegment {
        val sel = ObjCRuntime.sel("negotiatedTLSProtocolVersion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property negotiatedTLSCipherSuite
    open fun negotiatedTLSCipherSuite(): MemorySegment {
        val sel = ObjCRuntime.sel("negotiatedTLSCipherSuite")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cellular
    open fun isCellular(): BOOL {
        val sel = ObjCRuntime.sel("isCellular")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property expensive
    open fun isExpensive(): BOOL {
        val sel = ObjCRuntime.sel("isExpensive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property constrained
    open fun isConstrained(): BOOL {
        val sel = ObjCRuntime.sel("isConstrained")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property multipath
    open fun isMultipath(): BOOL {
        val sel = ObjCRuntime.sel("isMultipath")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property domainResolutionProtocol
    open fun domainResolutionProtocol(): NSURLSessionTaskMetricsDomainResolutionProtocol {
        val sel = ObjCRuntime.sel("domainResolutionProtocol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionTaskMetricsDomainResolutionProtocol
    }
    
}

