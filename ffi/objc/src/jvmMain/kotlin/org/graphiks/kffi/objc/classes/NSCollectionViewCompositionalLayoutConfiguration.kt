package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewCompositionalLayoutConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionViewCompositionalLayoutConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewCompositionalLayoutConfiguration") }
        
    }
    
    // @property scrollDirection
    open fun scrollDirection(): NSCollectionViewScrollDirection {
        val sel = ObjCRuntime.sel("scrollDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionViewScrollDirection
    }
    open fun setScrollDirection(value: NSCollectionViewScrollDirection) {
        val sel = ObjCRuntime.sel("setScrollDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interSectionSpacing
    open fun interSectionSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("interSectionSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setInterSectionSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setInterSectionSpacing:")
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
    
}

