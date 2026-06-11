/**
 * Kotlin/JVM wrapper for Objective-C class: NSMachBootstrapServer
 * Superclass: NSPortNameServer
 */
open class NSMachBootstrapServer(ptr: MemorySegment) : NSPortNameServer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMachBootstrapServer") }
        
        fun sharedInstance(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedInstance")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun portForName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun portForName(name: String): MemorySegment = portForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun portForName_host(name: MemorySegment, host: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:host:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, host) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun portForName_host(name: String, host: String): MemorySegment = portForName_host(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), host))
    
    fun registerPort_name(port: MemorySegment, name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerPort:name:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, port, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerPort_name(port: MemorySegment, name: String): BOOL = registerPort_name(port, ObjCRuntime.newNSString(Arena.global(), name))
    
    fun servicePortWithName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("servicePortWithName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun servicePortWithName(name: String): MemorySegment = servicePortWithName(ObjCRuntime.newNSString(Arena.global(), name))
    
}

