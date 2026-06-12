package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSocketPortNameServer
 * Superclass: NSPortNameServer
 */
open class NSSocketPortNameServer(ptr: MemorySegment) : NSPortNameServer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSocketPortNameServer") }
        
        fun sharedInstance(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedInstance")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    override fun `portForName`(name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun portForName(name: String): MemorySegment = portForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    override fun `portForName_host`(name: MemorySegment, host: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:host:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, host) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun portForName_host(name: String, host: String): MemorySegment = portForName_host(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), host))
    
    override fun `registerPort_name`(port: MemorySegment, name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerPort:name:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, port, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerPort_name(port: MemorySegment, name: String): BOOL = registerPort_name(port, ObjCRuntime.newNSString(Arena.global(), name))
    
    override fun `removePortForName`(name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removePortForName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removePortForName(name: String): BOOL = removePortForName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun portForName_host_nameServerPortNumber(name: MemorySegment, host: MemorySegment, portNumber: uint16_t): MemorySegment {
        val sel = ObjCRuntime.sel("portForName:host:nameServerPortNumber:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, host, portNumber) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun portForName_host_nameServerPortNumber(name: String, host: String, portNumber: uint16_t): MemorySegment = portForName_host_nameServerPortNumber(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), host), portNumber)
    
    fun registerPort_name_nameServerPortNumber(port: MemorySegment, name: MemorySegment, portNumber: uint16_t): BOOL {
        val sel = ObjCRuntime.sel("registerPort:name:nameServerPortNumber:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, port, name, portNumber) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerPort_name_nameServerPortNumber(port: MemorySegment, name: String, portNumber: uint16_t): BOOL = registerPort_name_nameServerPortNumber(port, ObjCRuntime.newNSString(Arena.global(), name), portNumber)
    
    // @property defaultNameServerPortNumber
    fun defaultNameServerPortNumber(): uint16_t {
        val sel = ObjCRuntime.sel("defaultNameServerPortNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as uint16_t
    }
    fun setDefaultNameServerPortNumber(value: uint16_t) {
        val sel = ObjCRuntime.sel("setDefaultNameServerPortNumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

