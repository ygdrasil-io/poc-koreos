package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableSet
 * Superclass: NSSet
 */
open class NSMutableSet(ptr: MemorySegment) : NSSet(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableSet") }
        
    }
    
    fun addObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    override fun `init`(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCapacity(numItems: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCapacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numItems) as MemorySegment
    }
    
}

// ── Category: NSExtendedMutableSet on NSMutableSet ─────────────────────────────────────────

fun NSMutableSet.addObjectsFromArray(array: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObjectsFromArray:")
    ObjCRuntime.msgSend(null, ptr, sel, array)
}

fun NSMutableSet.intersectSet(otherSet: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("intersectSet:")
    ObjCRuntime.msgSend(null, ptr, sel, otherSet)
}

fun NSMutableSet.minusSet(otherSet: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("minusSet:")
    ObjCRuntime.msgSend(null, ptr, sel, otherSet)
}

fun NSMutableSet.removeAllObjects(): Unit {
    val sel = ObjCRuntime.sel("removeAllObjects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSMutableSet.unionSet(otherSet: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("unionSet:")
    ObjCRuntime.msgSend(null, ptr, sel, otherSet)
}

fun NSMutableSet.setSet(otherSet: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setSet:")
    ObjCRuntime.msgSend(null, ptr, sel, otherSet)
}

// ── Category: NSMutableSetCreation on NSMutableSet ─────────────────────────────────────────

// Class<*> method: +[NSMutableSet setWithCapacity:]
fun NSMutableSet_setWithCapacity(numItems: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("setWithCapacity:")
    val cls = ObjCRuntime.getClass("NSMutableSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, numItems) as MemorySegment
}

// ── Category: NSPredicateSupport on NSMutableSet ─────────────────────────────────────────

fun NSMutableSet.filterUsingPredicate(predicate: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("filterUsingPredicate:")
    ObjCRuntime.msgSend(null, ptr, sel, predicate)
}

