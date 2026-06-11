/**
 * Kotlin/JVM wrapper for Objective-C class: NSXPCConnection
 * Superclass: NSObject
 * Protocols: NSXPCProxyCreating
 */
open class NSXPCConnection(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXPCConnection") }
        
        fun currentConnection(): MemorySegment {
            val sel = ObjCRuntime.sel("currentConnection")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithServiceName(serviceName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithServiceName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, serviceName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithServiceName(serviceName: String): MemorySegment = initWithServiceName(ObjCRuntime.newNSString(Arena.global(), serviceName))
    
    fun initWithMachServiceName_options(name: MemorySegment, options: NSXPCConnectionOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMachServiceName:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, options) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithMachServiceName_options(name: String, options: NSXPCConnectionOptions): MemorySegment = initWithMachServiceName_options(ObjCRuntime.newNSString(Arena.global(), name), options)
    
    fun initWithListenerEndpoint(endpoint: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithListenerEndpoint:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, endpoint) as MemorySegment
    }
    
    fun remoteObjectProxyWithErrorHandler(handler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("remoteObjectProxyWithErrorHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, handler) as MemorySegment
    }
    
    fun synchronousRemoteObjectProxyWithErrorHandler(handler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("synchronousRemoteObjectProxyWithErrorHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, handler) as MemorySegment
    }
    
    fun resume(): Unit {
        val sel = ObjCRuntime.sel("resume")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun suspend(): Unit {
        val sel = ObjCRuntime.sel("suspend")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun activate(): Unit {
        val sel = ObjCRuntime.sel("activate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scheduleSendBarrierBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("scheduleSendBarrierBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun setCodeSigningRequirement(requirement: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCodeSigningRequirement:")
        ObjCRuntime.msgSend(null, ptr, sel, requirement)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setCodeSigningRequirement(requirement: String): Unit = setCodeSigningRequirement(ObjCRuntime.newNSString(Arena.global(), requirement))
    
    // @property serviceName
    fun serviceName(): MemorySegment {
        val sel = ObjCRuntime.sel("serviceName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun serviceNameAsString(): String = ObjCRuntime.toJavaString(serviceName())
    
    // @property endpoint
    fun endpoint(): MemorySegment {
        val sel = ObjCRuntime.sel("endpoint")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property exportedInterface
    fun exportedInterface(): MemorySegment {
        val sel = ObjCRuntime.sel("exportedInterface")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExportedInterface(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExportedInterface:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property exportedObject
    fun exportedObject(): MemorySegment {
        val sel = ObjCRuntime.sel("exportedObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExportedObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExportedObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property remoteObjectInterface
    fun remoteObjectInterface(): MemorySegment {
        val sel = ObjCRuntime.sel("remoteObjectInterface")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRemoteObjectInterface(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRemoteObjectInterface:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property remoteObjectProxy
    fun remoteObjectProxy(): MemorySegment {
        val sel = ObjCRuntime.sel("remoteObjectProxy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property interruptionHandler
    fun interruptionHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("interruptionHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInterruptionHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInterruptionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property invalidationHandler
    fun invalidationHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("invalidationHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInvalidationHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInvalidationHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property auditSessionIdentifier
    fun auditSessionIdentifier(): au_asid_t {
        val sel = ObjCRuntime.sel("auditSessionIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as au_asid_t
    }
    
    // @property processIdentifier
    fun processIdentifier(): pid_t {
        val sel = ObjCRuntime.sel("processIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as pid_t
    }
    
    // @property effectiveUserIdentifier
    fun effectiveUserIdentifier(): uid_t {
        val sel = ObjCRuntime.sel("effectiveUserIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as uid_t
    }
    
    // @property effectiveGroupIdentifier
    fun effectiveGroupIdentifier(): gid_t {
        val sel = ObjCRuntime.sel("effectiveGroupIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as gid_t
    }
    
}

