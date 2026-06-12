package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLProtocol
 * Superclass: NSObject
 */
open class NSURLProtocol(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLProtocol") }
        
        open fun canInitWithRequest(request: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithRequest:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, request) as BOOL
        }
        
        open fun canonicalRequestForRequest(request: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("canonicalRequestForRequest:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, request) as MemorySegment
        }
        
        open fun requestIsCacheEquivalent_toRequest(a: MemorySegment, b: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("requestIsCacheEquivalent:toRequest:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, a, b) as BOOL
        }
        
        open fun propertyForKey_inRequest(key: MemorySegment, request: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("propertyForKey:inRequest:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key, request) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun propertyForKey_inRequest(key: String, request: MemorySegment): MemorySegment = propertyForKey_inRequest(ObjCRuntime.newNSString(Arena.global(), key), request)
        
        open fun setProperty_forKey_inRequest(value: MemorySegment, key: MemorySegment, request: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setProperty:forKey:inRequest:")
            ObjCRuntime.msgSend(null, _class, sel, value, key, request)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun setProperty_forKey_inRequest(value: MemorySegment, key: String, request: MemorySegment): Unit = setProperty_forKey_inRequest(value, ObjCRuntime.newNSString(Arena.global(), key), request)
        
        open fun removePropertyForKey_inRequest(key: MemorySegment, request: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("removePropertyForKey:inRequest:")
            ObjCRuntime.msgSend(null, _class, sel, key, request)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun removePropertyForKey_inRequest(key: String, request: MemorySegment): Unit = removePropertyForKey_inRequest(ObjCRuntime.newNSString(Arena.global(), key), request)
        
        open fun registerClass(protocolClass: Class<*>): BOOL {
            val sel = ObjCRuntime.sel("registerClass:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, protocolClass) as BOOL
        }
        
        open fun unregisterClass(protocolClass: Class<*>): Unit {
            val sel = ObjCRuntime.sel("unregisterClass:")
            ObjCRuntime.msgSend(null, _class, sel, protocolClass)
        }
        
    }
    
    open fun initWithRequest_cachedResponse_client(request: MemorySegment, cachedResponse: MemorySegment, client: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRequest:cachedResponse:client:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, cachedResponse, client) as MemorySegment
    }
    
    open fun startLoading(): Unit {
        val sel = ObjCRuntime.sel("startLoading")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun stopLoading(): Unit {
        val sel = ObjCRuntime.sel("stopLoading")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property client
    /** @return id<NSURLProtocolClient> */
    open fun client(): MemorySegment {
        val sel = ObjCRuntime.sel("client")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property request
    open fun request(): MemorySegment {
        val sel = ObjCRuntime.sel("request")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cachedResponse
    open fun cachedResponse(): MemorySegment {
        val sel = ObjCRuntime.sel("cachedResponse")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

// ── Category: NSURLSessionTaskAdditions on NSURLProtocol ─────────────────────────────────────────

fun NSURLProtocol.initWithTask_cachedResponse_client(task: MemorySegment, cachedResponse: MemorySegment, client: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTask:cachedResponse:client:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, task, cachedResponse, client) as MemorySegment
}

fun NSURLProtocol.task(): MemorySegment {
    val sel = ObjCRuntime.sel("task")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSURLProtocol canInitWithTask:]
fun NSURLProtocol_canInitWithTask(task: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("canInitWithTask:")
    val cls = ObjCRuntime.getClass("NSURLProtocol")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel, task) as BOOL
}

// @property task
fun NSURLProtocol.task(): MemorySegment {
    val sel = ObjCRuntime.sel("task")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

