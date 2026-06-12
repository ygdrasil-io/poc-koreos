package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDistantObject
 * Superclass: NSProxy
 * Protocols: NSCoding
 */
open class NSDistantObject(ptr: MemorySegment) : NSProxy(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDistantObject") }
        
        fun proxyWithTarget_connection(target: MemorySegment, connection: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("proxyWithTarget:connection:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, target, connection) as MemorySegment
        }
        
        fun proxyWithLocal_connection(target: MemorySegment, connection: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("proxyWithLocal:connection:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, target, connection) as MemorySegment
        }
        
    }
    
    fun initWithTarget_connection(target: MemorySegment, connection: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTarget:connection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, connection) as MemorySegment
    }
    
    fun initWithLocal_connection(target: MemorySegment, connection: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocal:connection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, connection) as MemorySegment
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun setProtocolForProxy(proto: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setProtocolForProxy:")
        ObjCRuntime.msgSend(null, ptr, sel, proto)
    }
    
    // @property connectionForProxy
    fun connectionForProxy(): MemorySegment {
        val sel = ObjCRuntime.sel("connectionForProxy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _knownSelectors: MemorySegment
    // ivar: _wireCount: NSUInteger
    // ivar: _refCount: NSUInteger
    // ivar: _proto: MemorySegment
    // ivar: ___2: uint16_t
    // ivar: ___1: uint8_t
    // ivar: _wireType: uint8_t
    // ivar: _remoteClass: MemorySegment
}

