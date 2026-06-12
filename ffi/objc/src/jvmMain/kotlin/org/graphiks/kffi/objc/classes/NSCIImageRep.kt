package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCIImageRep
 * Superclass: NSImageRep
 */
open class NSCIImageRep(ptr: MemorySegment) : NSImageRep(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCIImageRep") }
        
        fun imageRepWithCIImage(image: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithCIImage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, image) as MemorySegment
        }
        
    }
    
    fun initWithCIImage(image: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCIImage:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, image) as MemorySegment
    }
    
    // @property CIImage
    fun CIImage(): MemorySegment {
        val sel = ObjCRuntime.sel("CIImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

