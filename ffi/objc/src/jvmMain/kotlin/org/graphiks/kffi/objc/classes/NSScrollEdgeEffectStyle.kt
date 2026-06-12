package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrollEdgeEffectStyle
 * Superclass: NSObject
 */
open class NSScrollEdgeEffectStyle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrollEdgeEffectStyle") }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun automaticStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("automaticStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun softStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("softStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun hardStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("hardStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property automaticStyle
}

