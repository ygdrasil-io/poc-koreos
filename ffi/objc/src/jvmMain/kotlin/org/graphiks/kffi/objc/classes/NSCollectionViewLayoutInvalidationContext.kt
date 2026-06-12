package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewLayoutInvalidationContext
 * Superclass: NSObject
 */
open class NSCollectionViewLayoutInvalidationContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewLayoutInvalidationContext") }
        
    }
    
    open fun invalidateItemsAtIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateItemsAtIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    open fun invalidateSupplementaryElementsOfKind_atIndexPaths(elementKind: NSCollectionViewSupplementaryElementKind, indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateSupplementaryElementsOfKind:atIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, elementKind, indexPaths)
    }
    
    open fun invalidateDecorationElementsOfKind_atIndexPaths(elementKind: NSCollectionViewDecorationElementKind, indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateDecorationElementsOfKind:atIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, elementKind, indexPaths)
    }
    
    // @property invalidateEverything
    open fun invalidateEverything(): BOOL {
        val sel = ObjCRuntime.sel("invalidateEverything")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property invalidateDataSourceCounts
    open fun invalidateDataSourceCounts(): BOOL {
        val sel = ObjCRuntime.sel("invalidateDataSourceCounts")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property invalidatedItemIndexPaths
    /** @return NSSet<NSIndexPath *> * */
    open fun invalidatedItemIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("invalidatedItemIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property invalidatedSupplementaryIndexPaths
    /** @return NSDictionary<NSCollectionViewSupplementaryElementKind,NSSet<NSIndexPath *> *> * */
    open fun invalidatedSupplementaryIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("invalidatedSupplementaryIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property invalidatedDecorationIndexPaths
    /** @return NSDictionary<NSCollectionViewDecorationElementKind,NSSet<NSIndexPath *> *> * */
    open fun invalidatedDecorationIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("invalidatedDecorationIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property contentOffsetAdjustment
    open fun contentOffsetAdjustment(): NSPoint {
        val sel = ObjCRuntime.sel("contentOffsetAdjustment")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    open fun setContentOffsetAdjustment(value: NSPoint) {
        val sel = ObjCRuntime.sel("setContentOffsetAdjustment:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    // @property contentSizeAdjustment
    open fun contentSizeAdjustment(): NSSize {
        val sel = ObjCRuntime.sel("contentSizeAdjustment")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    open fun setContentSizeAdjustment(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentSizeAdjustment:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
}

