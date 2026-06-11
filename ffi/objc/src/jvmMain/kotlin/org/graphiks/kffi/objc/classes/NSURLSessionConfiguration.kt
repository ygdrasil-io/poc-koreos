/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSURLSessionConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionConfiguration") }
        
        fun backgroundSessionConfigurationWithIdentifier(identifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("backgroundSessionConfigurationWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun backgroundSessionConfigurationWithIdentifier(identifier: String): MemorySegment = backgroundSessionConfigurationWithIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun defaultSessionConfiguration(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultSessionConfiguration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ephemeralSessionConfiguration(): MemorySegment {
            val sel = ObjCRuntime.sel("ephemeralSessionConfiguration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaultSessionConfiguration
    fun defaultSessionConfiguration(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultSessionConfiguration")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property ephemeralSessionConfiguration
    fun ephemeralSessionConfiguration(): MemorySegment {
        val sel = ObjCRuntime.sel("ephemeralSessionConfiguration")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property identifier
    fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun identifierAsString(): String = ObjCRuntime.toJavaString(identifier())
    
    // @property requestCachePolicy
    fun requestCachePolicy(): NSURLRequestCachePolicy {
        val sel = ObjCRuntime.sel("requestCachePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestCachePolicy
    }
    fun setRequestCachePolicy(value: NSURLRequestCachePolicy) {
        val sel = ObjCRuntime.sel("setRequestCachePolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeoutIntervalForRequest
    fun timeoutIntervalForRequest(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeoutIntervalForRequest")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setTimeoutIntervalForRequest(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTimeoutIntervalForRequest:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeoutIntervalForResource
    fun timeoutIntervalForResource(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeoutIntervalForResource")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setTimeoutIntervalForResource(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTimeoutIntervalForResource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property networkServiceType
    fun networkServiceType(): NSURLRequestNetworkServiceType {
        val sel = ObjCRuntime.sel("networkServiceType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLRequestNetworkServiceType
    }
    fun setNetworkServiceType(value: NSURLRequestNetworkServiceType) {
        val sel = ObjCRuntime.sel("setNetworkServiceType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsCellularAccess
    fun allowsCellularAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsCellularAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsCellularAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsCellularAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsExpensiveNetworkAccess
    fun allowsExpensiveNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsExpensiveNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsExpensiveNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsExpensiveNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsConstrainedNetworkAccess
    fun allowsConstrainedNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsConstrainedNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsConstrainedNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsUltraConstrainedNetworkAccess
    fun allowsUltraConstrainedNetworkAccess(): BOOL {
        val sel = ObjCRuntime.sel("allowsUltraConstrainedNetworkAccess")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsUltraConstrainedNetworkAccess(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsUltraConstrainedNetworkAccess:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property requiresDNSSECValidation
    fun requiresDNSSECValidation(): BOOL {
        val sel = ObjCRuntime.sel("requiresDNSSECValidation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRequiresDNSSECValidation(value: BOOL) {
        val sel = ObjCRuntime.sel("setRequiresDNSSECValidation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property waitsForConnectivity
    fun waitsForConnectivity(): BOOL {
        val sel = ObjCRuntime.sel("waitsForConnectivity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setWaitsForConnectivity(value: BOOL) {
        val sel = ObjCRuntime.sel("setWaitsForConnectivity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property discretionary
    fun isDiscretionary(): BOOL {
        val sel = ObjCRuntime.sel("isDiscretionary")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDiscretionary(value: BOOL) {
        val sel = ObjCRuntime.sel("setDiscretionary:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sharedContainerIdentifier
    fun sharedContainerIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedContainerIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSharedContainerIdentifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSharedContainerIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun sharedContainerIdentifierAsString(): String = ObjCRuntime.toJavaString(sharedContainerIdentifier())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSharedContainerIdentifier(value: String) = setSharedContainerIdentifier(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property sessionSendsLaunchEvents
    fun sessionSendsLaunchEvents(): BOOL {
        val sel = ObjCRuntime.sel("sessionSendsLaunchEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSessionSendsLaunchEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setSessionSendsLaunchEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property connectionProxyDictionary
    fun connectionProxyDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("connectionProxyDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setConnectionProxyDictionary(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setConnectionProxyDictionary:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property TLSMinimumSupportedProtocol
    fun TLSMinimumSupportedProtocol(): SSLProtocol {
        val sel = ObjCRuntime.sel("TLSMinimumSupportedProtocol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as SSLProtocol
    }
    fun setTLSMinimumSupportedProtocol(value: SSLProtocol) {
        val sel = ObjCRuntime.sel("setTLSMinimumSupportedProtocol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property TLSMaximumSupportedProtocol
    fun TLSMaximumSupportedProtocol(): SSLProtocol {
        val sel = ObjCRuntime.sel("TLSMaximumSupportedProtocol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as SSLProtocol
    }
    fun setTLSMaximumSupportedProtocol(value: SSLProtocol) {
        val sel = ObjCRuntime.sel("setTLSMaximumSupportedProtocol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property TLSMinimumSupportedProtocolVersion
    fun TLSMinimumSupportedProtocolVersion(): tls_protocol_version_t {
        val sel = ObjCRuntime.sel("TLSMinimumSupportedProtocolVersion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as tls_protocol_version_t
    }
    fun setTLSMinimumSupportedProtocolVersion(value: tls_protocol_version_t) {
        val sel = ObjCRuntime.sel("setTLSMinimumSupportedProtocolVersion:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property TLSMaximumSupportedProtocolVersion
    fun TLSMaximumSupportedProtocolVersion(): tls_protocol_version_t {
        val sel = ObjCRuntime.sel("TLSMaximumSupportedProtocolVersion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as tls_protocol_version_t
    }
    fun setTLSMaximumSupportedProtocolVersion(value: tls_protocol_version_t) {
        val sel = ObjCRuntime.sel("setTLSMaximumSupportedProtocolVersion:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPShouldUsePipelining
    fun HTTPShouldUsePipelining(): BOOL {
        val sel = ObjCRuntime.sel("HTTPShouldUsePipelining")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHTTPShouldUsePipelining(value: BOOL) {
        val sel = ObjCRuntime.sel("setHTTPShouldUsePipelining:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPShouldSetCookies
    fun HTTPShouldSetCookies(): BOOL {
        val sel = ObjCRuntime.sel("HTTPShouldSetCookies")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHTTPShouldSetCookies(value: BOOL) {
        val sel = ObjCRuntime.sel("setHTTPShouldSetCookies:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPCookieAcceptPolicy
    fun HTTPCookieAcceptPolicy(): NSHTTPCookieAcceptPolicy {
        val sel = ObjCRuntime.sel("HTTPCookieAcceptPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSHTTPCookieAcceptPolicy
    }
    fun setHTTPCookieAcceptPolicy(value: NSHTTPCookieAcceptPolicy) {
        val sel = ObjCRuntime.sel("setHTTPCookieAcceptPolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPAdditionalHeaders
    fun HTTPAdditionalHeaders(): MemorySegment {
        val sel = ObjCRuntime.sel("HTTPAdditionalHeaders")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHTTPAdditionalHeaders(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHTTPAdditionalHeaders:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPMaximumConnectionsPerHost
    fun HTTPMaximumConnectionsPerHost(): NSInteger {
        val sel = ObjCRuntime.sel("HTTPMaximumConnectionsPerHost")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setHTTPMaximumConnectionsPerHost(value: NSInteger) {
        val sel = ObjCRuntime.sel("setHTTPMaximumConnectionsPerHost:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property HTTPCookieStorage
    fun HTTPCookieStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("HTTPCookieStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHTTPCookieStorage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHTTPCookieStorage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property URLCredentialStorage
    fun URLCredentialStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("URLCredentialStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURLCredentialStorage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURLCredentialStorage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property URLCache
    fun URLCache(): MemorySegment {
        val sel = ObjCRuntime.sel("URLCache")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURLCache(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURLCache:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldUseExtendedBackgroundIdleMode
    fun shouldUseExtendedBackgroundIdleMode(): BOOL {
        val sel = ObjCRuntime.sel("shouldUseExtendedBackgroundIdleMode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldUseExtendedBackgroundIdleMode(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldUseExtendedBackgroundIdleMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property protocolClasses
    /** @return NSArray<Class> * */
    fun protocolClasses(): MemorySegment {
        val sel = ObjCRuntime.sel("protocolClasses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setProtocolClasses(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProtocolClasses:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property multipathServiceType
    fun multipathServiceType(): NSURLSessionMultipathServiceType {
        val sel = ObjCRuntime.sel("multipathServiceType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionMultipathServiceType
    }
    fun setMultipathServiceType(value: NSURLSessionMultipathServiceType) {
        val sel = ObjCRuntime.sel("setMultipathServiceType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesClassicLoadingMode
    fun usesClassicLoadingMode(): BOOL {
        val sel = ObjCRuntime.sel("usesClassicLoadingMode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesClassicLoadingMode(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesClassicLoadingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enablesEarlyData
    fun enablesEarlyData(): BOOL {
        val sel = ObjCRuntime.sel("enablesEarlyData")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnablesEarlyData(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnablesEarlyData:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSURLSessionDeprecated on NSURLSessionConfiguration ─────────────────────────────────────────

// Class method: +[NSURLSessionConfiguration backgroundSessionConfiguration:]
fun NSURLSessionConfiguration_backgroundSessionConfiguration(identifier: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("backgroundSessionConfiguration:")
    val cls = ObjCRuntime.getClass("NSURLSessionConfiguration")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, identifier) as MemorySegment
}

