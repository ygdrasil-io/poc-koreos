package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutSize
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutSize(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutSize") }
        
        open fun sizeWithWidthDimension_heightDimension(width: MemorySegment, height: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sizeWithWidthDimension:heightDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, width, height) as MemorySegment
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
    
    // @property widthDimension
    open fun widthDimension(): MemorySegment {
        val sel = ObjCRuntime.sel("widthDimension")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property heightDimension
    open fun heightDimension(): MemorySegment {
        val sel = ObjCRuntime.sel("heightDimension")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

