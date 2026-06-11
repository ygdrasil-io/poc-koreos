/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionTaskTransactionMetrics
 * Superclass: NSObject
 */
open class NSURLSessionTaskTransactionMetrics(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionTaskTransactionMetrics") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property request
    fun request(): MemorySegment {
        val sel = ObjCRuntime.sel("request")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property response
    fun response(): MemorySegment {
        val sel = ObjCRuntime.sel("response")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fetchStartDate
    fun fetchStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("fetchStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property domainLookupStartDate
    fun domainLookupStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("domainLookupStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property domainLookupEndDate
    fun domainLookupEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("domainLookupEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property connectStartDate
    fun connectStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("connectStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secureConnectionStartDate
    fun secureConnectionStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("secureConnectionStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secureConnectionEndDate
    fun secureConnectionEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("secureConnectionEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property connectEndDate
    fun connectEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("connectEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestStartDate
    fun requestStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("requestStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestEndDate
    fun requestEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("requestEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property responseStartDate
    fun responseStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("responseStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property responseEndDate
    fun responseEndDate(): MemorySegment {
        val sel = ObjCRuntime.sel("responseEndDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property networkProtocolName
    fun networkProtocolName(): MemorySegment {
        val sel = ObjCRuntime.sel("networkProtocolName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun networkProtocolNameAsString(): String = ObjCRuntime.toJavaString(networkProtocolName())
    
    // @property proxyConnection
    fun isProxyConnection(): BOOL {
        val sel = ObjCRuntime.sel("isProxyConnection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property reusedConnection
    fun isReusedConnection(): BOOL {
        val sel = ObjCRuntime.sel("isReusedConnection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resourceFetchType
    fun resourceFetchType(): NSURLSessionTaskMetricsResourceFetchType {
        val sel = ObjCRuntime.sel("resourceFetchType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionTaskMetricsResourceFetchType
    }
    
    // @property countOfRequestHeaderBytesSent
    fun countOfRequestHeaderBytesSent(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestHeaderBytesSent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfRequestBodyBytesSent
    fun countOfRequestBodyBytesSent(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestBodyBytesSent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfRequestBodyBytesBeforeEncoding
    fun countOfRequestBodyBytesBeforeEncoding(): int64_t {
        val sel = ObjCRuntime.sel("countOfRequestBodyBytesBeforeEncoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseHeaderBytesReceived
    fun countOfResponseHeaderBytesReceived(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseHeaderBytesReceived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseBodyBytesReceived
    fun countOfResponseBodyBytesReceived(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseBodyBytesReceived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfResponseBodyBytesAfterDecoding
    fun countOfResponseBodyBytesAfterDecoding(): int64_t {
        val sel = ObjCRuntime.sel("countOfResponseBodyBytesAfterDecoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property localAddress
    fun localAddress(): MemorySegment {
        val sel = ObjCRuntime.sel("localAddress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localAddressAsString(): String = ObjCRuntime.toJavaString(localAddress())
    
    // @property localPort
    fun localPort(): MemorySegment {
        val sel = ObjCRuntime.sel("localPort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property remoteAddress
    fun remoteAddress(): MemorySegment {
        val sel = ObjCRuntime.sel("remoteAddress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun remoteAddressAsString(): String = ObjCRuntime.toJavaString(remoteAddress())
    
    // @property remotePort
    fun remotePort(): MemorySegment {
        val sel = ObjCRuntime.sel("remotePort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property negotiatedTLSProtocolVersion
    fun negotiatedTLSProtocolVersion(): MemorySegment {
        val sel = ObjCRuntime.sel("negotiatedTLSProtocolVersion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property negotiatedTLSCipherSuite
    fun negotiatedTLSCipherSuite(): MemorySegment {
        val sel = ObjCRuntime.sel("negotiatedTLSCipherSuite")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cellular
    fun isCellular(): BOOL {
        val sel = ObjCRuntime.sel("isCellular")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property expensive
    fun isExpensive(): BOOL {
        val sel = ObjCRuntime.sel("isExpensive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property constrained
    fun isConstrained(): BOOL {
        val sel = ObjCRuntime.sel("isConstrained")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property multipath
    fun isMultipath(): BOOL {
        val sel = ObjCRuntime.sel("isMultipath")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property domainResolutionProtocol
    fun domainResolutionProtocol(): NSURLSessionTaskMetricsDomainResolutionProtocol {
        val sel = ObjCRuntime.sel("domainResolutionProtocol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionTaskMetricsDomainResolutionProtocol
    }
    
}

