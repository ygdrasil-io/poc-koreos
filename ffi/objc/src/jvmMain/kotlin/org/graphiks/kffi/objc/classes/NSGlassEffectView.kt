/**
 * Kotlin/JVM wrapper for Objective-C class: NSGlassEffectView
 * Superclass: NSView
 */
open class NSGlassEffectView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGlassEffectView") }
        
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
    
    // @property cornerRadius
    fun cornerRadius(): CGFloat {
        val sel = ObjCRuntime.sel("cornerRadius")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setCornerRadius(value: CGFloat) {
        val sel = ObjCRuntime.sel("setCornerRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tintColor
    fun tintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("tintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTintColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTintColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property style
    fun style(): NSGlassEffectViewStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGlassEffectViewStyle
    }
    fun setStyle(value: NSGlassEffectViewStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

