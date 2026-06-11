/**
 * Kotlin/JVM wrapper for Objective-C class: NSHost
 * Superclass: NSObject
 */
open class NSHost(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHost") }
        
        fun currentHost(): MemorySegment {
            val sel = ObjCRuntime.sel("currentHost")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hostWithName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("hostWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun hostWithName(name: String): MemorySegment = hostWithName(ObjCRuntime.newNSString(Arena.global(), name))
        
        fun hostWithAddress(address: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("hostWithAddress:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, address) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun hostWithAddress(address: String): MemorySegment = hostWithAddress(ObjCRuntime.newNSString(Arena.global(), address))
        
        fun setHostCacheEnabled(flag: BOOL): Unit {
            val sel = ObjCRuntime.sel("setHostCacheEnabled:")
            ObjCRuntime.msgSend(null, _class, sel, flag)
        }
        
        fun isHostCacheEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isHostCacheEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun flushHostCache(): Unit {
            val sel = ObjCRuntime.sel("flushHostCache")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
    }
    
    fun isEqualToHost(aHost: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToHost:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aHost) as BOOL
    }
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property names
    /** @return NSArray<NSString *> * */
    fun names(): MemorySegment {
        val sel = ObjCRuntime.sel("names")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property address
    fun address(): MemorySegment {
        val sel = ObjCRuntime.sel("address")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun addressAsString(): String = ObjCRuntime.toJavaString(address())
    
    // @property addresses
    /** @return NSArray<NSString *> * */
    fun addresses(): MemorySegment {
        val sel = ObjCRuntime.sel("addresses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedName
    fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: names: MemorySegment
    // ivar: addresses: MemorySegment
    // ivar: reserved: MemorySegment
}

