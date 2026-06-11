/**
 * Kotlin/JVM wrapper for Objective-C class: CAOpenGLLayer
 * Superclass: CALayer
 */
open class CAOpenGLLayer(ptr: MemorySegment) : CALayer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("CAOpenGLLayer") }
        
    }
    
    fun canDrawInCGLContext_pixelFormat_forLayerTime_displayTime(ctx: MemorySegment, pf: MemorySegment, t: CFTimeInterval, ts: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canDrawInCGLContext:pixelFormat:forLayerTime:displayTime:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ctx, pf, t, ts) as BOOL
    }
    
    fun drawInCGLContext_pixelFormat_forLayerTime_displayTime(ctx: MemorySegment, pf: MemorySegment, t: CFTimeInterval, ts: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawInCGLContext:pixelFormat:forLayerTime:displayTime:")
        ObjCRuntime.msgSend(null, ptr, sel, ctx, pf, t, ts)
    }
    
    fun copyCGLPixelFormatForDisplayMask(mask: uint32_t): MemorySegment {
        val sel = ObjCRuntime.sel("copyCGLPixelFormatForDisplayMask:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask) as MemorySegment
    }
    
    fun releaseCGLPixelFormat(pf: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("releaseCGLPixelFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, pf)
    }
    
    fun copyCGLContextForPixelFormat(pf: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("copyCGLContextForPixelFormat:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pf) as MemorySegment
    }
    
    fun releaseCGLContext(ctx: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("releaseCGLContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ctx)
    }
    
    // @property asynchronous
    fun isAsynchronous(): BOOL {
        val sel = ObjCRuntime.sel("isAsynchronous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAsynchronous(value: BOOL) {
        val sel = ObjCRuntime.sel("setAsynchronous:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property colorspace
    fun colorspace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorspace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColorspace(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColorspace:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsExtendedDynamicRangeContent
    fun wantsExtendedDynamicRangeContent(): BOOL {
        val sel = ObjCRuntime.sel("wantsExtendedDynamicRangeContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setWantsExtendedDynamicRangeContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setWantsExtendedDynamicRangeContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _glPriv: MemorySegment
}

