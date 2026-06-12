package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSViewLayoutRegion
 * Superclass: NSObject
 */
open class NSViewLayoutRegion(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSViewLayoutRegion") }
        
        open fun safeAreaLayoutRegionWithCornerAdaptation(adaptivityAxis: NSViewLayoutRegionAdaptivityAxis): MemorySegment {
            val sel = ObjCRuntime.sel("safeAreaLayoutRegionWithCornerAdaptation:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, adaptivityAxis) as MemorySegment
        }
        
        open fun marginsLayoutRegionWithCornerAdaptation(adaptivityAxis: NSViewLayoutRegionAdaptivityAxis): MemorySegment {
            val sel = ObjCRuntime.sel("marginsLayoutRegionWithCornerAdaptation:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, adaptivityAxis) as MemorySegment
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
    
}

