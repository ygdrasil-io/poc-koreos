package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPortNameServer
 * Superclass: NSObject
 */
open class NSPortNameServer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPortNameServer") }
        
        open fun systemDefaultPortNameServer(): MemorySegment {
            val sel = ObjCRuntime.sel("systemDefaultPortNameServer")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun portForName(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun portForName(name: String): MemorySegment = portForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    open fun portForName_host(name: MemorySegment, host: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:host:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, host) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun portForName_host(name: String, host: String): MemorySegment = portForName_host(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), host))
    
    open fun registerPort_name(port: MemorySegment, name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerPort:name:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, port, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun registerPort_name(port: MemorySegment, name: String): BOOL = registerPort_name(port, ObjCRuntime.newNSString(Arena.global(), name))
    
    open fun removePortForName(name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removePortForName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removePortForName(name: String): BOOL = removePortForName(ObjCRuntime.newNSString(Arena.global(), name))
    
}

