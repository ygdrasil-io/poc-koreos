package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberSelectionStyle
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScrubberSelectionStyle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberSelectionStyle") }
        
        open fun outlineOverlayStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("outlineOverlayStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun roundedBackgroundStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("roundedBackgroundStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun makeSelectionView(): MemorySegment {
        val sel = ObjCRuntime.sel("makeSelectionView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property outlineOverlayStyle
    }
    
    // @property roundedBackgroundStyle
    }
    
}

