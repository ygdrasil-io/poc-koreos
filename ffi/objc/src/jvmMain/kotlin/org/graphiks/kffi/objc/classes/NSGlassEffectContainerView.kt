/**
 * Kotlin/JVM wrapper for Objective-C class: NSGlassEffectContainerView
 * Superclass: NSView
 */
open class NSGlassEffectContainerView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGlassEffectContainerView") }
        
    }
    
    // @property contentView
    fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property spacing
    fun spacing(): CGFloat {
        val sel = ObjCRuntime.sel("spacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

