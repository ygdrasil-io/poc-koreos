package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSocketPort
 * Superclass: NSPort
 */
open class NSSocketPort(ptr: MemorySegment) : NSPort(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSocketPort") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithTCPPort(port: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTCPPort:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, port) as MemorySegment
    }
    
    fun initWithProtocolFamily_socketType_protocol_address(family: Int, type: Int, protocol: Int, address: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithProtocolFamily:socketType:protocol:address:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, family, type, protocol, address) as MemorySegment
    }
    
    fun initWithProtocolFamily_socketType_protocol_socket(family: Int, type: Int, protocol: Int, sock: NSSocketNativeHandle): MemorySegment {
        val sel = ObjCRuntime.sel("initWithProtocolFamily:socketType:protocol:socket:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, family, type, protocol, sock) as MemorySegment
    }
    
    fun initRemoteWithTCPPort_host(port: Any, hostName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initRemoteWithTCPPort:host:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, port, hostName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initRemoteWithTCPPort_host(port: Any, hostName: String): MemorySegment = initRemoteWithTCPPort_host(port, ObjCRuntime.newNSString(Arena.global(), hostName))
    
    fun initRemoteWithProtocolFamily_socketType_protocol_address(family: Int, type: Int, protocol: Int, address: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initRemoteWithProtocolFamily:socketType:protocol:address:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, family, type, protocol, address) as MemorySegment
    }
    
    // @property protocolFamily
    fun protocolFamily(): Int {
        val sel = ObjCRuntime.sel("protocolFamily")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property socketType
    fun socketType(): Int {
        val sel = ObjCRuntime.sel("socketType")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property protocol
    fun protocol(): Int {
        val sel = ObjCRuntime.sel("protocol")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property address
    fun address(): MemorySegment {
        val sel = ObjCRuntime.sel("address")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property socket
    fun socket(): NSSocketNativeHandle {
        val sel = ObjCRuntime.sel("socket")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as NSSocketNativeHandle
    }
    
}

