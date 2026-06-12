package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSImageCell
 * Superclass: NSCell
 * Protocols: NSCopying, NSCoding
 */
open class NSImageCell(ptr: MemorySegment) : NSCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSImageCell") }
        
    }
    
    // @property imageAlignment
    fun imageAlignment(): NSImageAlignment {
        val sel = ObjCRuntime.sel("imageAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageAlignment
    }
    fun setImageAlignment(value: NSImageAlignment) {
        val sel = ObjCRuntime.sel("setImageAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageScaling
    fun imageScaling(): NSImageScaling {
        val sel = ObjCRuntime.sel("imageScaling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageScaling
    }
    fun setImageScaling(value: NSImageScaling) {
        val sel = ObjCRuntime.sel("setImageScaling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageFrameStyle
    fun imageFrameStyle(): NSImageFrameStyle {
        val sel = ObjCRuntime.sel("imageFrameStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageFrameStyle
    }
    fun setImageFrameStyle(value: NSImageFrameStyle) {
        val sel = ObjCRuntime.sel("setImageFrameStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

