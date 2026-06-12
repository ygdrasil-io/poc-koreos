package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorSampler
 * Superclass: NSObject
 */
open class NSColorSampler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorSampler") }
        
    }
    
    open fun showSamplerWithSelectionHandler(selectionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showSamplerWithSelectionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, selectionHandler)
    }
    
}

