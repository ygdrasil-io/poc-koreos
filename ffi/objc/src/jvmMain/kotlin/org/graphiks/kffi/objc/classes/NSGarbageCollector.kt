package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGarbageCollector
 * Superclass: NSObject
 */
open class NSGarbageCollector(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGarbageCollector") }
        
        open fun defaultCollector(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultCollector")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun isCollecting(): BOOL {
        val sel = ObjCRuntime.sel("isCollecting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun disable(): Unit {
        val sel = ObjCRuntime.sel("disable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun enable(): Unit {
        val sel = ObjCRuntime.sel("enable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun collectIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("collectIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun collectExhaustively(): Unit {
        val sel = ObjCRuntime.sel("collectExhaustively")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun disableCollectorForPointer(ptr: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("disableCollectorForPointer:")
        ObjCRuntime.msgSend(null, ptr, sel, ptr)
    }
    
    open fun enableCollectorForPointer(ptr: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enableCollectorForPointer:")
        ObjCRuntime.msgSend(null, ptr, sel, ptr)
    }
    
    open fun zone(): MemorySegment {
        val sel = ObjCRuntime.sel("zone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

