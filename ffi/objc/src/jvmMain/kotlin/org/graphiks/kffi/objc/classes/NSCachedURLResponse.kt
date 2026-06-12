package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCachedURLResponse
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSCachedURLResponse(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCachedURLResponse") }
        
    }
    
    open fun initWithResponse_data(response: MemorySegment, `data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithResponse:data:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, response, `data`) as MemorySegment
    }
    
    open fun initWithResponse_data_userInfo_storagePolicy(response: MemorySegment, `data`: MemorySegment, userInfo: MemorySegment, storagePolicy: NSURLCacheStoragePolicy): MemorySegment {
        val sel = ObjCRuntime.sel("initWithResponse:data:userInfo:storagePolicy:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, response, `data`, userInfo, storagePolicy) as MemorySegment
    }
    
    // @property response
    open fun response(): MemorySegment {
        val sel = ObjCRuntime.sel("response")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property data
    open fun data(): MemorySegment {
        val sel = ObjCRuntime.sel("data")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property userInfo
    open fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property storagePolicy
    open fun storagePolicy(): NSURLCacheStoragePolicy {
        val sel = ObjCRuntime.sel("storagePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLCacheStoragePolicy
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

