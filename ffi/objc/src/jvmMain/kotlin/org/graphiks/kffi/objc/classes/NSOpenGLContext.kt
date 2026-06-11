/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenGLContext
 * Superclass: NSObject
 * Protocols: NSLocking
 */
open class NSOpenGLContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenGLContext") }
        
        fun clearCurrentContext(): Unit {
            val sel = ObjCRuntime.sel("clearCurrentContext")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        fun currentContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithFormat_shareContext(format: MemorySegment, share: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFormat:shareContext:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, share) as MemorySegment
    }
    
    fun initWithCGLContextObj(context: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGLContextObj:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, context) as MemorySegment
    }
    
    fun setFullScreen(): Unit {
        val sel = ObjCRuntime.sel("setFullScreen")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setOffScreen_width_height_rowbytes(baseaddr: MemorySegment, width: GLsizei, height: GLsizei, rowbytes: GLint): Unit {
        val sel = ObjCRuntime.sel("setOffScreen:width:height:rowbytes:")
        ObjCRuntime.msgSend(null, ptr, sel, baseaddr, width, height, rowbytes)
    }
    
    fun clearDrawable(): Unit {
        val sel = ObjCRuntime.sel("clearDrawable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun update(): Unit {
        val sel = ObjCRuntime.sel("update")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun flushBuffer(): Unit {
        val sel = ObjCRuntime.sel("flushBuffer")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makeCurrentContext(): Unit {
        val sel = ObjCRuntime.sel("makeCurrentContext")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun copyAttributesFromContext_withMask(context: MemorySegment, mask: GLbitfield): Unit {
        val sel = ObjCRuntime.sel("copyAttributesFromContext:withMask:")
        ObjCRuntime.msgSend(null, ptr, sel, context, mask)
    }
    
    fun setValues_forParameter(vals: MemorySegment, param: NSOpenGLContextParameter): Unit {
        val sel = ObjCRuntime.sel("setValues:forParameter:")
        ObjCRuntime.msgSend(null, ptr, sel, vals, param)
    }
    
    fun getValues_forParameter(vals: MemorySegment, param: NSOpenGLContextParameter): Unit {
        val sel = ObjCRuntime.sel("getValues:forParameter:")
        ObjCRuntime.msgSend(null, ptr, sel, vals, param)
    }
    
    fun createTexture_fromView_internalFormat(target: GLenum, view: MemorySegment, format: GLenum): Unit {
        val sel = ObjCRuntime.sel("createTexture:fromView:internalFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, target, view, format)
    }
    
    // @property pixelFormat
    fun pixelFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("pixelFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property currentContext
    fun currentContext(): MemorySegment {
        val sel = ObjCRuntime.sel("currentContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentVirtualScreen
    fun currentVirtualScreen(): GLint {
        val sel = ObjCRuntime.sel("currentVirtualScreen")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLint
    }
    fun setCurrentVirtualScreen(value: GLint) {
        val sel = ObjCRuntime.sel("setCurrentVirtualScreen:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property CGLContextObj
    fun CGLContextObj(): MemorySegment {
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

