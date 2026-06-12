package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutSection
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutSection(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutSection") }
        
        open fun sectionWithGroup(group: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sectionWithGroup:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, group) as MemorySegment
        }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property contentInsets
    open fun contentInsets(): NSDirectionalEdgeInsets {
        val sel = ObjCRuntime.sel("contentInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("leading"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("trailing")).withName("NSDirectionalEdgeInsets"), ptr, sel) as NSDirectionalEdgeInsets
    }
    open fun setContentInsets(value: NSDirectionalEdgeInsets) {
        val sel = ObjCRuntime.sel("setContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("leading"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("trailing")).withName("NSDirectionalEdgeInsets")))
    }
    
    // @property interGroupSpacing
    open fun interGroupSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("interGroupSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setInterGroupSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setInterGroupSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property orthogonalScrollingBehavior
    open fun orthogonalScrollingBehavior(): NSCollectionLayoutSectionOrthogonalScrollingBehavior {
        val sel = ObjCRuntime.sel("orthogonalScrollingBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionLayoutSectionOrthogonalScrollingBehavior
    }
    open fun setOrthogonalScrollingBehavior(value: NSCollectionLayoutSectionOrthogonalScrollingBehavior) {
        val sel = ObjCRuntime.sel("setOrthogonalScrollingBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property boundarySupplementaryItems
    /** @return NSArray<NSCollectionLayoutBoundarySupplementaryItem *> * */
    open fun boundarySupplementaryItems(): MemorySegment {
        val sel = ObjCRuntime.sel("boundarySupplementaryItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBoundarySupplementaryItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBoundarySupplementaryItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property supplementariesFollowContentInsets
    open fun supplementariesFollowContentInsets(): BOOL {
        val sel = ObjCRuntime.sel("supplementariesFollowContentInsets")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSupplementariesFollowContentInsets(value: BOOL) {
        val sel = ObjCRuntime.sel("setSupplementariesFollowContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visibleItemsInvalidationHandler
    open fun visibleItemsInvalidationHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("visibleItemsInvalidationHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setVisibleItemsInvalidationHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVisibleItemsInvalidationHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property decorationItems
    /** @return NSArray<NSCollectionLayoutDecorationItem *> * */
    open fun decorationItems(): MemorySegment {
        val sel = ObjCRuntime.sel("decorationItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDecorationItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDecorationItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

