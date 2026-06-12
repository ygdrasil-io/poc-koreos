package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDistributedLock
 * Superclass: NSObject
 */
open class NSDistributedLock(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDistributedLock") }
        
        open fun lockWithPath(path: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("lockWithPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun lockWithPath(path: String): MemorySegment = lockWithPath(ObjCRuntime.newNSString(Arena.global(), path))
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithPath(path: String): MemorySegment = initWithPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    open fun tryLock(): BOOL {
        val sel = ObjCRuntime.sel("tryLock")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun unlock(): Unit {
        val sel = ObjCRuntime.sel("unlock")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun breakLock(): Unit {
        val sel = ObjCRuntime.sel("breakLock")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property lockDate
    open fun lockDate(): MemorySegment {
        val sel = ObjCRuntime.sel("lockDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

