/**
 * Kotlin/JVM wrapper for Objective-C class: NSNetServiceBrowser
 * Superclass: NSObject
 */
open class NSNetServiceBrowser(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNetServiceBrowser") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun scheduleInRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun removeFromRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun searchForBrowsableDomains(): Unit {
        val sel = ObjCRuntime.sel("searchForBrowsableDomains")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun searchForRegistrationDomains(): Unit {
        val sel = ObjCRuntime.sel("searchForRegistrationDomains")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun searchForServicesOfType_inDomain(type: MemorySegment, domainString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("searchForServicesOfType:inDomain:")
        ObjCRuntime.msgSend(null, ptr, sel, type, domainString)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun searchForServicesOfType_inDomain(type: String, domainString: String): Unit = searchForServicesOfType_inDomain(ObjCRuntime.newNSString(Arena.global(), type), ObjCRuntime.newNSString(Arena.global(), domainString))
    
    fun stop(): Unit {
        val sel = ObjCRuntime.sel("stop")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSNetServiceBrowserDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesPeerToPeer
    fun includesPeerToPeer(): BOOL {
        val sel = ObjCRuntime.sel("includesPeerToPeer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesPeerToPeer(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesPeerToPeer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _netServiceBrowser: MemorySegment
    // ivar: _delegate: MemorySegment
    // ivar: _reserved: MemorySegment
}

