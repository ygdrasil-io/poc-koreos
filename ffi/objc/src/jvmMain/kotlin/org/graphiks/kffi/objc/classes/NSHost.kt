package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSHost
 * Superclass: NSObject
 */
open class NSHost(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHost") }
        
        open fun currentHost(): MemorySegment {
            val sel = ObjCRuntime.sel("currentHost")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun hostWithName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("hostWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun hostWithName(name: String): MemorySegment = hostWithName(ObjCRuntime.newNSString(Arena.global(), name))
        
        open fun hostWithAddress(address: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("hostWithAddress:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, address) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun hostWithAddress(address: String): MemorySegment = hostWithAddress(ObjCRuntime.newNSString(Arena.global(), address))
        
        open fun setHostCacheEnabled(flag: BOOL): Unit {
            val sel = ObjCRuntime.sel("setHostCacheEnabled:")
            ObjCRuntime.msgSend(null, _class, sel, flag)
        }
        
        open fun isHostCacheEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isHostCacheEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun flushHostCache(): Unit {
            val sel = ObjCRuntime.sel("flushHostCache")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
    }
    
    open fun isEqualToHost(aHost: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToHost:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aHost) as BOOL
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property names
    /** @return NSArray<NSString *> * */
    open fun names(): MemorySegment {
        val sel = ObjCRuntime.sel("names")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property address
    open fun address(): MemorySegment {
        val sel = ObjCRuntime.sel("address")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun addressAsString(): String = ObjCRuntime.toJavaString(address())
    
    // @property addresses
    /** @return NSArray<NSString *> * */
    open fun addresses(): MemorySegment {
        val sel = ObjCRuntime.sel("addresses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedName
    open fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: names: MemorySegment
    // ivar: addresses: MemorySegment
    // ivar: reserved: MemorySegment
}

