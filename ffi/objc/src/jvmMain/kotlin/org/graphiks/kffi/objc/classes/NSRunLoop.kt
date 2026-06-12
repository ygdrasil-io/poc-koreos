package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRunLoop
 * Superclass: NSObject
 */
open class NSRunLoop(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRunLoop") }
        
        open fun currentRunLoop(): MemorySegment {
            val sel = ObjCRuntime.sel("currentRunLoop")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun mainRunLoop(): MemorySegment {
            val sel = ObjCRuntime.sel("mainRunLoop")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun getCFRunLoop(): MemorySegment {
        val sel = ObjCRuntime.sel("getCFRunLoop")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun addTimer_forMode(timer: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("addTimer:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, timer, mode)
    }
    
    open fun addPort_forMode(aPort: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("addPort:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aPort, mode)
    }
    
    open fun removePort_forMode(aPort: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removePort:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aPort, mode)
    }
    
    open fun limitDateForMode(mode: NSRunLoopMode): MemorySegment {
        val sel = ObjCRuntime.sel("limitDateForMode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mode) as MemorySegment
    }
    
    open fun acceptInputForMode_beforeDate(mode: NSRunLoopMode, limitDate: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("acceptInputForMode:beforeDate:")
        ObjCRuntime.msgSend(null, ptr, sel, mode, limitDate)
    }
    
    // @property currentRunLoop
    }
    
    // @property mainRunLoop
    }
    
    // @property currentMode
    open fun currentMode(): NSRunLoopMode {
        val sel = ObjCRuntime.sel("currentMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRunLoopMode
    }
    
}

// ── Category: NSRunLoopConveniences on NSRunLoop ─────────────────────────────────────────

fun NSRunLoop.run(): Unit {
    val sel = ObjCRuntime.sel("run")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSRunLoop.runUntilDate(limitDate: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("runUntilDate:")
    ObjCRuntime.msgSend(null, ptr, sel, limitDate)
}

fun NSRunLoop.runMode_beforeDate(mode: NSRunLoopMode, limitDate: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("runMode:beforeDate:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, mode, limitDate) as BOOL
}

fun NSRunLoop.configureAsServer(): Unit {
    val sel = ObjCRuntime.sel("configureAsServer")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSRunLoop.performInModes_block(modes: MemorySegment, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performInModes:block:")
    ObjCRuntime.msgSend(null, ptr, sel, modes, block)
}

fun NSRunLoop.performBlock(block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, block)
}

// ── Category: NSOrderedPerform on NSRunLoop ─────────────────────────────────────────

fun NSRunLoop.performSelector_target_argument_order_modes(aSelector: MemorySegment, target: MemorySegment, arg: MemorySegment, order: NSUInteger, modes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performSelector:target:argument:order:modes:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, target, arg, order, modes)
}

fun NSRunLoop.cancelPerformSelector_target_argument(aSelector: MemorySegment, target: MemorySegment, arg: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("cancelPerformSelector:target:argument:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, target, arg)
}

fun NSRunLoop.cancelPerformSelectorsWithTarget(target: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("cancelPerformSelectorsWithTarget:")
    ObjCRuntime.msgSend(null, ptr, sel, target)
}

