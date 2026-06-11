/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenGLPixelBuffer
 * Superclass: NSObject
 */
open class NSOpenGLPixelBuffer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenGLPixelBuffer") }
        
    }
    
    fun initWithTextureTarget_textureInternalFormat_textureMaxMipMapLevel_pixelsWide_pixelsHigh(target: GLenum, format: GLenum, maxLevel: GLint, pixelsWide: GLsizei, pixelsHigh: GLsizei): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTextureTarget:textureInternalFormat:textureMaxMipMapLevel:pixelsWide:pixelsHigh:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, format, maxLevel, pixelsWide, pixelsHigh) as MemorySegment
    }
    
    fun initWithCGLPBufferObj(pbuffer: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGLPBufferObj:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pbuffer) as MemorySegment
    }
    
    // @property CGLPBufferObj
    fun CGLPBufferObj(): MemorySegment {
        val sel = ObjCRuntime.sel("CGLPBufferObj")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property pixelsWide
    fun pixelsWide(): GLsizei {
        val sel = ObjCRuntime.sel("pixelsWide")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLsizei
    }
    
    // @property pixelsHigh
    fun pixelsHigh(): GLsizei {
        val sel = ObjCRuntime.sel("pixelsHigh")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLsizei
    }
    
    // @property textureTarget
    fun textureTarget(): GLenum {
        val sel = ObjCRuntime.sel("textureTarget")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLenum
    }
    
    // @property textureInternalFormat
    fun textureInternalFormat(): GLenum {
        val sel = ObjCRuntime.sel("textureInternalFormat")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLenum
    }
    
    // @property textureMaxMipMapLevel
    fun textureMaxMipMapLevel(): GLint {
        val sel = ObjCRuntime.sel("textureMaxMipMapLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLint
    }
    
}

