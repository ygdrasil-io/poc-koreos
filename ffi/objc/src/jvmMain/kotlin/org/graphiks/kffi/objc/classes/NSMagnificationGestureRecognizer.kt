package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMagnificationGestureRecognizer
 * Superclass: NSGestureRecognizer
 */
open class NSMagnificationGestureRecognizer(ptr: MemorySegment) : NSGestureRecognizer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMagnificationGestureRecognizer") }
        
    }
    
    // @property magnification
    fun magnification(): CGFloat {
        val sel = ObjCRuntime.sel("magnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMagnification(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMagnification:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

