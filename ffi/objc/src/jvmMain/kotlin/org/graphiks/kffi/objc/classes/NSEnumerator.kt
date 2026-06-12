package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSEnumerator
 * Superclass: NSObject
 * Protocols: NSFastEnumeration
 */
open class NSEnumerator(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSEnumerator") }
        
    }
    
    open fun nextObject(): MemorySegment {
        val sel = ObjCRuntime.sel("nextObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSExtendedEnumerator on NSEnumerator ─────────────────────────────────────────

/** @return NSArray<ObjectType> * */
fun NSEnumerator.allObjects(): MemorySegment {
    val sel = ObjCRuntime.sel("allObjects")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property allObjects
/** @return NSArray<ObjectType> * */
    val sel = ObjCRuntime.sel("allObjects")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

