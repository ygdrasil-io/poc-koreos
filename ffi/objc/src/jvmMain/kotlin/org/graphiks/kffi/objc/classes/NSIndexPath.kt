package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSIndexPath
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSIndexPath(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSIndexPath") }
        
        open fun indexPathWithIndex(index: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("indexPathWithIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, index) as MemorySegment
        }
        
        open fun indexPathWithIndexes_length(indexes: MemorySegment, length: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("indexPathWithIndexes:length:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, indexes, length) as MemorySegment
        }
        
    }
    
    open fun initWithIndexes_length(indexes: MemorySegment, length: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIndexes:length:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexes, length) as MemorySegment
    }
    
    open fun initWithIndex(index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    open fun indexPathByAddingIndex(index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathByAddingIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    open fun indexPathByRemovingLastIndex(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathByRemovingLastIndex")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun indexAtPosition(position: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("indexAtPosition:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, position) as NSUInteger
    }
    
    open fun getIndexes_range(indexes: MemorySegment, positionRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("getIndexes:range:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, ObjCRuntime.ObjCStructArg(positionRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun compare(otherObject: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherObject) as NSComparisonResult
    }
    
    // @property length
    open fun length(): NSUInteger {
        val sel = ObjCRuntime.sel("length")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

// ── Category: NSDeprecated on NSIndexPath ─────────────────────────────────────────

fun NSIndexPath.getIndexes(indexes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getIndexes:")
    ObjCRuntime.msgSend(null, ptr, sel, indexes)
}

// ── Category: NSCollectionViewAdditions on NSIndexPath ─────────────────────────────────────────

fun NSIndexPath.item(): NSInteger {
    val sel = ObjCRuntime.sel("item")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSIndexPath.section(): NSInteger {
    val sel = ObjCRuntime.sel("section")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// Class<*> method: +[NSIndexPath indexPathForItem:inSection:]
fun NSIndexPath_indexPathForItem_inSection(item: NSInteger, section: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("indexPathForItem:inSection:")
    val cls = ObjCRuntime.getClass("NSIndexPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, item, section) as MemorySegment
}

// @property item