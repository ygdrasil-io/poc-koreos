/**
 * Kotlin/JVM wrapper for Objective-C class: NSNetService
 * Superclass: NSObject
 */
open class NSNetService(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNetService") }
        
        /** @return NSDictionary<NSString *,NSData *> * */
        fun dictionaryFromTXTRecordData(txtData: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dictionaryFromTXTRecordData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, txtData) as MemorySegment
        }
        
        fun dataFromTXTRecordDictionary(txtDictionary: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dataFromTXTRecordDictionary:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, txtDictionary) as MemorySegment
        }
        
    }
    
    fun initWithDomain_type_name_port(domain: MemorySegment, type: MemorySegment, name: MemorySegment, port: Int): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDomain:type:name:port:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domain, type, name, port) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithDomain_type_name_port(domain: String, type: String, name: String, port: Int): MemorySegment = initWithDomain_type_name_port(ObjCRuntime.newNSString(Arena.global(), domain), ObjCRuntime.newNSString(Arena.global(), type), ObjCRuntime.newNSString(Arena.global(), name), port)
    
    fun initWithDomain_type_name(domain: MemorySegment, type: MemorySegment, name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDomain:type:name:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domain, type, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithDomain_type_name(domain: String, type: String, name: String): MemorySegment = initWithDomain_type_name(ObjCRuntime.newNSString(Arena.global(), domain), ObjCRuntime.newNSString(Arena.global(), type), ObjCRuntime.newNSString(Arena.global(), name))
    
    fun scheduleInRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun removeFromRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun publish(): Unit {
        val sel = ObjCRuntime.sel("publish")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun publishWithOptions(options: NSNetServiceOptions): Unit {
        val sel = ObjCRuntime.sel("publishWithOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, options)
    }
    
    fun resolve(): Unit {
        val sel = ObjCRuntime.sel("resolve")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stop(): Unit {
        val sel = ObjCRuntime.sel("stop")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resolveWithTimeout(timeout: NSTimeInterval): Unit {
        val sel = ObjCRuntime.sel("resolveWithTimeout:")
        ObjCRuntime.msgSend(null, ptr, sel, timeout)
    }
    
    fun getInputStream_outputStream(inputStream: MemorySegment, outputStream: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getInputStream:outputStream:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, inputStream, outputStream) as BOOL
    }
    
    fun setTXTRecordData(recordData: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setTXTRecordData:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, recordData) as BOOL
    }
    
    fun TXTRecordData(): MemorySegment {
        val sel = ObjCRuntime.sel("TXTRecordData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun startMonitoring(): Unit {
        val sel = ObjCRuntime.sel("startMonitoring")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stopMonitoring(): Unit {
        val sel = ObjCRuntime.sel("stopMonitoring")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSNetServiceDelegate> */
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
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property type
    fun type(): MemorySegment {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun typeAsString(): String = ObjCRuntime.toJavaString(type())
    
    // @property domain
    fun domain(): MemorySegment {
        val sel = ObjCRuntime.sel("domain")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun domainAsString(): String = ObjCRuntime.toJavaString(domain())
    
    // @property hostName
    fun hostName(): MemorySegment {
        val sel = ObjCRuntime.sel("hostName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun hostNameAsString(): String = ObjCRuntime.toJavaString(hostName())
    
    // @property addresses
    /** @return NSArray<NSData *> * */
    fun addresses(): MemorySegment {
        val sel = ObjCRuntime.sel("addresses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property port
    fun port(): NSInteger {
        val sel = ObjCRuntime.sel("port")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _netService: MemorySegment
    // ivar: _delegate: MemorySegment
    // ivar: _reserved: MemorySegment
}

