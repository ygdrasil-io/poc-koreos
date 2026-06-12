package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLHandle
 * Superclass: NSObject
 */
open class NSURLHandle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLHandle") }
        
        open fun registerURLHandleClass(anURLHandleSubclass: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("registerURLHandleClass:")
            ObjCRuntime.msgSend(null, _class, sel, anURLHandleSubclass)
        }
        
        open fun URLHandleClassForURL(anURL: MemorySegment): Class<*> {
            val sel = ObjCRuntime.sel("URLHandleClassForURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anURL) as Class<*>
        }
        
        open fun canInitWithURL(anURL: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithURL:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, anURL) as BOOL
        }
        
        open fun cachedHandleForURL(anURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("cachedHandleForURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, anURL) as MemorySegment
        }
        
    }
    
    open fun status(): NSURLHandleStatus {
        val sel = ObjCRuntime.sel("status")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLHandleStatus
    }
    
    open fun failureReason(): MemorySegment {
        val sel = ObjCRuntime.sel("failureReason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun failureReasonAsString(): String = ObjCRuntime.toJavaString(failureReason())
    
    open fun addClient(client: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addClient:")
        ObjCRuntime.msgSend(null, ptr, sel, client)
    }
    
    open fun removeClient(client: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeClient:")
        ObjCRuntime.msgSend(null, ptr, sel, client)
    }
    
    open fun loadInBackground(): Unit {
        val sel = ObjCRuntime.sel("loadInBackground")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun cancelLoadInBackground(): Unit {
        val sel = ObjCRuntime.sel("cancelLoadInBackground")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun resourceData(): MemorySegment {
        val sel = ObjCRuntime.sel("resourceData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun availableResourceData(): MemorySegment {
        val sel = ObjCRuntime.sel("availableResourceData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun expectedResourceDataSize(): Long {
        val sel = ObjCRuntime.sel("expectedResourceDataSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
    }
    
    open fun flushCachedData(): Unit {
        val sel = ObjCRuntime.sel("flushCachedData")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun backgroundLoadDidFailWithReason(reason: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("backgroundLoadDidFailWithReason:")
        ObjCRuntime.msgSend(null, ptr, sel, reason)
    }
    
    open fun didLoadBytes_loadComplete(newBytes: MemorySegment, yorn: BOOL): Unit {
        val sel = ObjCRuntime.sel("didLoadBytes:loadComplete:")
        ObjCRuntime.msgSend(null, ptr, sel, newBytes, yorn)
    }
    
    open fun initWithURL_cached(anURL: MemorySegment, willCache: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:cached:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anURL, willCache) as MemorySegment
    }
    
    open fun propertyForKey(propertyKey: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("propertyForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, propertyKey) as MemorySegment
    }
    
    open fun propertyForKeyIfAvailable(propertyKey: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("propertyForKeyIfAvailable:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, propertyKey) as MemorySegment
    }
    
    open fun writeProperty_forKey(propertyValue: MemorySegment, propertyKey: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("writeProperty:forKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, propertyValue, propertyKey) as BOOL
    }
    
    open fun writeData(`data`: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("writeData:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`) as BOOL
    }
    
    open fun loadInForeground(): MemorySegment {
        val sel = ObjCRuntime.sel("loadInForeground")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun beginLoadInBackground(): Unit {
        val sel = ObjCRuntime.sel("beginLoadInBackground")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun endLoadInBackground(): Unit {
        val sel = ObjCRuntime.sel("endLoadInBackground")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _clients: MemorySegment
    // ivar: _data: MemorySegment
    // ivar: _status: NSURLHandleStatus
    // ivar: _reserved: NSInteger
}

