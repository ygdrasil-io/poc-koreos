package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenGLLayer
 * Superclass: CAOpenGLLayer
 */
open class NSOpenGLLayer(ptr: MemorySegment) : CAOpenGLLayer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenGLLayer") }
        
    }
    
    fun openGLPixelFormatForDisplayMask(mask: uint32_t): MemorySegment {
        val sel = ObjCRuntime.sel("openGLPixelFormatForDisplayMask:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask) as MemorySegment
    }
    
    fun openGLContextForPixelFormat(pixelFormat: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("openGLContextForPixelFormat:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pixelFormat) as MemorySegment
    }
    
    fun canDrawInOpenGLContext_pixelFormat_forLayerTime_displayTime(context: MemorySegment, pixelFormat: MemorySegment, t: CFTimeInterval, ts: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canDrawInOpenGLContext:pixelFormat:forLayerTime:displayTime:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, context, pixelFormat, t, ts) as BOOL
    }
    
    fun drawInOpenGLContext_pixelFormat_forLayerTime_displayTime(context: MemorySegment, pixelFormat: MemorySegment, t: CFTimeInterval, ts: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawInOpenGLContext:pixelFormat:forLayerTime:displayTime:")
        ObjCRuntime.msgSend(null, ptr, sel, context, pixelFormat, t, ts)
    }
    
    // @property view
    fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property openGLPixelFormat
    fun openGLPixelFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("openGLPixelFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOpenGLPixelFormat(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOpenGLPixelFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property openGLContext
    fun openGLContext(): MemorySegment {
        val sel = ObjCRuntime.sel("openGLContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOpenGLContext(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOpenGLContext:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

