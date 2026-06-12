package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutDimension
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutDimension(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutDimension") }
        
        open fun fractionalWidthDimension(fractionalWidth: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fractionalWidthDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fractionalWidth) as MemorySegment
        }
        
        open fun fractionalHeightDimension(fractionalHeight: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fractionalHeightDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fractionalHeight) as MemorySegment
        }
        
        open fun absoluteDimension(absoluteDimension: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("absoluteDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, absoluteDimension) as MemorySegment
        }
        
        open fun estimatedDimension(estimatedDimension: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("estimatedDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, estimatedDimension) as MemorySegment
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
    
    // @property isFractionalWidth
    open fun isFractionalWidth(): BOOL {
        val sel = ObjCRuntime.sel("isFractionalWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isFractionalHeight
    open fun isFractionalHeight(): BOOL {
        val sel = ObjCRuntime.sel("isFractionalHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isAbsolute
    open fun isAbsolute(): BOOL {
        val sel = ObjCRuntime.sel("isAbsolute")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isEstimated
    open fun isEstimated(): BOOL {
        val sel = ObjCRuntime.sel("isEstimated")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property dimension
    open fun dimension(): CGFloat {
        val sel = ObjCRuntime.sel("dimension")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

