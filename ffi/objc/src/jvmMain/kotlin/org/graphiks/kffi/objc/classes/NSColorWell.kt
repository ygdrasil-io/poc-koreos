/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorWell
 * Superclass: NSControl
 */
open class NSColorWell(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorWell") }
        
        fun colorWellWithStyle(style: NSColorWellStyle): MemorySegment {
            val sel = ObjCRuntime.sel("colorWellWithStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, style) as MemorySegment
        }
        
    }
    
    fun deactivate(): Unit {
        val sel = ObjCRuntime.sel("deactivate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun activate(exclusive: BOOL): Unit {
        val sel = ObjCRuntime.sel("activate:")
        ObjCRuntime.msgSend(null, ptr, sel, exclusive)
    }
    
    fun drawWellInside(insideRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawWellInside:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(insideRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun takeColorFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeColorFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property active
    fun isActive(): BOOL {
        val sel = ObjCRuntime.sel("isActive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property bordered
    fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property color
    fun color(): MemorySegment {
        val sel = ObjCRuntime.sel("color")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property colorWellStyle
    fun colorWellStyle(): NSColorWellStyle {
        val sel = ObjCRuntime.sel("colorWellStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorWellStyle
    }
    fun setColorWellStyle(value: NSColorWellStyle) {
        val sel = ObjCRuntime.sel("setColorWellStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pulldownTarget
    fun pulldownTarget(): MemorySegment {
        val sel = ObjCRuntime.sel("pulldownTarget")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPulldownTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPulldownTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pulldownAction
    fun pulldownAction(): MemorySegment {
        val sel = ObjCRuntime.sel("pulldownAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPulldownAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPulldownAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property supportsAlpha
    fun supportsAlpha(): BOOL {
        val sel = ObjCRuntime.sel("supportsAlpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSupportsAlpha(value: BOOL) {
        val sel = ObjCRuntime.sel("setSupportsAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumLinearExposure
    fun maximumLinearExposure(): CGFloat {
        val sel = ObjCRuntime.sel("maximumLinearExposure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaximumLinearExposure(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumLinearExposure:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

