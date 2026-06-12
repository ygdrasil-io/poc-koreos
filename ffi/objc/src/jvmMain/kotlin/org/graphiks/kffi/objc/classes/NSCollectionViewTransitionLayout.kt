package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewTransitionLayout
 * Superclass: NSCollectionViewLayout
 */
open class NSCollectionViewTransitionLayout(ptr: MemorySegment) : NSCollectionViewLayout(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewTransitionLayout") }
        
    }
    
    fun initWithCurrentLayout_nextLayout(currentLayout: MemorySegment, newLayout: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCurrentLayout:nextLayout:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, currentLayout, newLayout) as MemorySegment
    }
    
    fun updateValue_forAnimatedKey(value: CGFloat, key: NSCollectionViewTransitionLayoutAnimatedKey): Unit {
        val sel = ObjCRuntime.sel("updateValue:forAnimatedKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    fun valueForAnimatedKey(key: NSCollectionViewTransitionLayoutAnimatedKey): CGFloat {
        val sel = ObjCRuntime.sel("valueForAnimatedKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, key) as CGFloat
    }
    
    // @property transitionProgress
    fun transitionProgress(): CGFloat {
        val sel = ObjCRuntime.sel("transitionProgress")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTransitionProgress(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTransitionProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentLayout
    fun currentLayout(): MemorySegment {
        val sel = ObjCRuntime.sel("currentLayout")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nextLayout
    fun nextLayout(): MemorySegment {
        val sel = ObjCRuntime.sel("nextLayout")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

