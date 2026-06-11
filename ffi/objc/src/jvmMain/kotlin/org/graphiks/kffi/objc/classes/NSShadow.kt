/**
 * Kotlin/JVM wrapper for Objective-C class: NSShadow
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSShadow(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSShadow") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun `set`(): Unit {
        val sel = ObjCRuntime.sel("set")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property shadowOffset
    fun shadowOffset(): NSSize {
        val sel = ObjCRuntime.sel("shadowOffset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setShadowOffset(value: NSSize) {
        val sel = ObjCRuntime.sel("setShadowOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property shadowBlurRadius
    fun shadowBlurRadius(): CGFloat {
        val sel = ObjCRuntime.sel("shadowBlurRadius")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setShadowBlurRadius(value: CGFloat) {
        val sel = ObjCRuntime.sel("setShadowBlurRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadowColor
    fun shadowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("shadowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShadowColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShadowColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

