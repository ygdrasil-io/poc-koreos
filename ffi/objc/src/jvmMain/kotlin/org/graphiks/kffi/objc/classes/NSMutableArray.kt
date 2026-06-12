package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableArray
 * Superclass: NSArray
 */
open class NSMutableArray(ptr: MemorySegment) : NSArray(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableArray") }
        
    }
    
    fun addObject(anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject)
    }
    
    fun insertObject_atIndex(anObject: MemorySegment, index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("insertObject:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject, index)
    }
    
    fun removeLastObject(): Unit {
        val sel = ObjCRuntime.sel("removeLastObject")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun removeObjectAtIndex(index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("removeObjectAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun replaceObjectAtIndex_withObject(index: NSUInteger, anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceObjectAtIndex:withObject:")
        ObjCRuntime.msgSend(null, ptr, sel, index, anObject)
    }
    
    override fun `init`(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCapacity(numItems: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCapacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numItems) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
}

// ── Category: NSExtendedMutableArray on NSMutableArray ─────────────────────────────────────────

fun NSMutableArray.addObjectsFromArray(otherArray: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObjectsFromArray:")
    ObjCRuntime.msgSend(null, ptr, sel, otherArray)
}

fun NSMutableArray.exchangeObjectAtIndex_withObjectAtIndex(idx1: NSUInteger, idx2: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("exchangeObjectAtIndex:withObjectAtIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, idx1, idx2)
}

fun NSMutableArray.removeAllObjects(): Unit {
    val sel = ObjCRuntime.sel("removeAllObjects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSMutableArray.removeObject_inRange(anObject: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("removeObject:inRange:")
    ObjCRuntime.msgSend(null, ptr, sel, anObject, range)
}

fun NSMutableArray.removeObject(anObject: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObject:")
    ObjCRuntime.msgSend(null, ptr, sel, anObject)
}

fun NSMutableArray.removeObjectIdenticalTo_inRange(anObject: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("removeObjectIdenticalTo:inRange:")
    ObjCRuntime.msgSend(null, ptr, sel, anObject, range)
}

fun NSMutableArray.removeObjectIdenticalTo(anObject: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObjectIdenticalTo:")
    ObjCRuntime.msgSend(null, ptr, sel, anObject)
}

fun NSMutableArray.removeObjectsFromIndices_numIndices(indices: MemorySegment, cnt: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("removeObjectsFromIndices:numIndices:")
    ObjCRuntime.msgSend(null, ptr, sel, indices, cnt)
}

fun NSMutableArray.removeObjectsInArray(otherArray: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObjectsInArray:")
    ObjCRuntime.msgSend(null, ptr, sel, otherArray)
}

fun NSMutableArray.removeObjectsInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("removeObjectsInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableArray.replaceObjectsInRange_withObjectsFromArray_range(range: NSRange, otherArray: MemorySegment, otherRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("replaceObjectsInRange:withObjectsFromArray:range:")
    ObjCRuntime.msgSend(null, ptr, sel, range, otherArray, otherRange)
}

fun NSMutableArray.replaceObjectsInRange_withObjectsFromArray(range: NSRange, otherArray: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceObjectsInRange:withObjectsFromArray:")
    ObjCRuntime.msgSend(null, ptr, sel, range, otherArray)
}

fun NSMutableArray.setArray(otherArray: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setArray:")
    ObjCRuntime.msgSend(null, ptr, sel, otherArray)
}

fun NSMutableArray.sortUsingFunction_context(compare: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sortUsingFunction:context:")
    ObjCRuntime.msgSend(null, ptr, sel, compare, context)
}

fun NSMutableArray.sortUsingSelector(comparator: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sortUsingSelector:")
    ObjCRuntime.msgSend(null, ptr, sel, comparator)
}

fun NSMutableArray.insertObjects_atIndexes(objects: MemorySegment, indexes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("insertObjects:atIndexes:")
    ObjCRuntime.msgSend(null, ptr, sel, objects, indexes)
}

fun NSMutableArray.removeObjectsAtIndexes(indexes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObjectsAtIndexes:")
    ObjCRuntime.msgSend(null, ptr, sel, indexes)
}

fun NSMutableArray.replaceObjectsAtIndexes_withObjects(indexes: MemorySegment, objects: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceObjectsAtIndexes:withObjects:")
    ObjCRuntime.msgSend(null, ptr, sel, indexes, objects)
}

fun NSMutableArray.setObject_atIndexedSubscript(obj: MemorySegment, idx: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setObject:atIndexedSubscript:")
    ObjCRuntime.msgSend(null, ptr, sel, obj, idx)
}

fun NSMutableArray.sortUsingComparator(cmptr: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sortUsingComparator:")
    ObjCRuntime.msgSend(null, ptr, sel, cmptr)
}

fun NSMutableArray.sortWithOptions_usingComparator(opts: NSSortOptions, cmptr: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sortWithOptions:usingComparator:")
    ObjCRuntime.msgSend(null, ptr, sel, opts, cmptr)
}

// ── Category: NSMutableArrayCreation on NSMutableArray ─────────────────────────────────────────

/** @return NSMutableArray<ObjectType> * */
fun NSMutableArray.initWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
}

/** @return NSMutableArray<ObjectType> * */
fun NSMutableArray.initWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
}

// Class<*> method: +[NSMutableArray arrayWithCapacity:]
fun NSMutableArray_arrayWithCapacity(numItems: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithCapacity:")
    val cls = ObjCRuntime.getClass("NSMutableArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, numItems) as MemorySegment
}

// Class<*> method: +[NSMutableArray arrayWithContentsOfFile:]
fun NSMutableArray_arrayWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithContentsOfFile:")
    val cls = ObjCRuntime.getClass("NSMutableArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path) as MemorySegment
}

// Class<*> method: +[NSMutableArray arrayWithContentsOfURL:]
fun NSMutableArray_arrayWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithContentsOfURL:")
    val cls = ObjCRuntime.getClass("NSMutableArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url) as MemorySegment
}

// ── Category: NSMutableArrayDiffing on NSMutableArray ─────────────────────────────────────────

fun NSMutableArray.applyDifference(difference: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("applyDifference:")
    ObjCRuntime.msgSend(null, ptr, sel, difference)
}

// ── Category: NSSortDescriptorSorting on NSMutableArray ─────────────────────────────────────────

fun NSMutableArray.sortUsingDescriptors(sortDescriptors: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sortUsingDescriptors:")
    ObjCRuntime.msgSend(null, ptr, sel, sortDescriptors)
}

// ── Category: NSPredicateSupport on NSMutableArray ─────────────────────────────────────────

fun NSMutableArray.filterUsingPredicate(predicate: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("filterUsingPredicate:")
    ObjCRuntime.msgSend(null, ptr, sel, predicate)
}

