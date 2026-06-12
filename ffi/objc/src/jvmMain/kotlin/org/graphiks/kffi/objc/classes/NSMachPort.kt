package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMachPort
 * Superclass: NSPort
 */
open class NSMachPort(ptr: MemorySegment) : NSPort(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMachPort") }
        
        fun portWithMachPort(machPort: uint32_t): MemorySegment {
            val sel = ObjCRuntime.sel("portWithMachPort:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, machPort) as MemorySegment
        }
        
        fun portWithMachPort_options(machPort: uint32_t, f: NSMachPortOptions): MemorySegment {
            val sel = ObjCRuntime.sel("portWithMachPort:options:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, machPort, f) as MemorySegment
        }
        
    }
    
    fun initWithMachPort(machPort: uint32_t): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMachPort:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, machPort) as MemorySegment
    }
    
    override fun `setDelegate`(anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject)
    }
    
    /** @return id<NSMachPortDelegate> */
    override fun `delegate`(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithMachPort_options(machPort: uint32_t, f: NSMachPortOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMachPort:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, machPort, f) as MemorySegment
    }
    
    override fun `scheduleInRunLoop_forMode`(runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, runLoop, mode)
    }
    
    override fun `removeFromRunLoop_forMode`(runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, runLoop, mode)
    }
    
    // @property machPort
    fun machPort(): uint32_t {
        val sel = ObjCRuntime.sel("machPort")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as uint32_t
    }
    
}

