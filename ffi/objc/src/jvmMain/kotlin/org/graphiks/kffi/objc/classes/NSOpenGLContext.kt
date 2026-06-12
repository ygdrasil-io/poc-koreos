package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenGLContext
 * Superclass: NSObject
 * Protocols: NSLocking
 */
open class NSOpenGLContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenGLContext") }
        
        open fun clearCurrentContext(): Unit {
            val sel = ObjCRuntime.sel("clearCurrentContext")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun currentContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithFormat_shareContext(format: MemorySegment, share: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFormat:shareContext:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, share) as MemorySegment
    }
    
    open fun initWithCGLContextObj(context: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGLContextObj:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, context) as MemorySegment
    }
    
    open fun setFullScreen(): Unit {
        val sel = ObjCRuntime.sel("setFullScreen")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun setOffScreen_width_height_rowbytes(baseaddr: MemorySegment, width: GLsizei, height: GLsizei, rowbytes: GLint): Unit {
        val sel = ObjCRuntime.sel("setOffScreen:width:height:rowbytes:")
        ObjCRuntime.msgSend(null, ptr, sel, baseaddr, width, height, rowbytes)
    }
    
    open fun clearDrawable(): Unit {
        val sel = ObjCRuntime.sel("clearDrawable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun update(): Unit {
        val sel = ObjCRuntime.sel("update")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun flushBuffer(): Unit {
        val sel = ObjCRuntime.sel("flushBuffer")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun makeCurrentContext(): Unit {
        val sel = ObjCRuntime.sel("makeCurrentContext")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun copyAttributesFromContext_withMask(context: MemorySegment, mask: GLbitfield): Unit {
        val sel = ObjCRuntime.sel("copyAttributesFromContext:withMask:")
        ObjCRuntime.msgSend(null, ptr, sel, context, mask)
    }
    
    open fun setValues_forParameter(vals: MemorySegment, param: NSOpenGLContextParameter): Unit {
        val sel = ObjCRuntime.sel("setValues:forParameter:")
        ObjCRuntime.msgSend(null, ptr, sel, vals, param)
    }
    
    open fun getValues_forParameter(vals: MemorySegment, param: NSOpenGLContextParameter): Unit {
        val sel = ObjCRuntime.sel("getValues:forParameter:")
        ObjCRuntime.msgSend(null, ptr, sel, vals, param)
    }
    
    open fun createTexture_fromView_internalFormat(target: GLenum, view: MemorySegment, format: GLenum): Unit {
        val sel = ObjCRuntime.sel("createTexture:fromView:internalFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, target, view, format)
    }
    
    // @property pixelFormat
    open fun pixelFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("pixelFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property view
    open fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentContext
    open fun currentVirtualScreen(): GLint {
        val sel = ObjCRuntime.sel("currentVirtualScreen")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLint
    }
    open fun setCurrentVirtualScreen(value: GLint) {
        val sel = ObjCRuntime.sel("setCurrentVirtualScreen:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property CGLContextObj
    open fun CGLContextObj(): MemorySegment {
        val sel = ObjCRuntime.sel("CGLContextObj")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSOpenGLPixelBuffer on NSOpenGLContext ─────────────────────────────────────────

fun NSOpenGLContext.setPixelBuffer_cubeMapFace_mipMapLevel_currentVirtualScreen(pixelBuffer: MemorySegment, face: GLenum, level: GLint, screen: GLint): Unit {
    val sel = ObjCRuntime.sel("setPixelBuffer:cubeMapFace:mipMapLevel:currentVirtualScreen:")
    ObjCRuntime.msgSend(null, ptr, sel, pixelBuffer, face, level, screen)
}

fun NSOpenGLContext.pixelBuffer(): MemorySegment {
    val sel = ObjCRuntime.sel("pixelBuffer")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSOpenGLContext.pixelBufferCubeMapFace(): GLenum {
    val sel = ObjCRuntime.sel("pixelBufferCubeMapFace")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLenum
}

fun NSOpenGLContext.pixelBufferMipMapLevel(): GLint {
    val sel = ObjCRuntime.sel("pixelBufferMipMapLevel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLint
}

fun NSOpenGLContext.setTextureImageToPixelBuffer_colorBuffer(pixelBuffer: MemorySegment, source: GLenum): Unit {
    val sel = ObjCRuntime.sel("setTextureImageToPixelBuffer:colorBuffer:")
    ObjCRuntime.msgSend(null, ptr, sel, pixelBuffer, source)
}

