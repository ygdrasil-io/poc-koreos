/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberImageItemView
 * Superclass: NSScrubberItemView
 */
open class NSScrubberImageItemView(ptr: MemorySegment) : NSScrubberItemView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberImageItemView") }
        
    }
    
    // @property imageView
    fun imageView(): MemorySegment {
        val sel = ObjCRuntime.sel("imageView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property imageAlignment
    fun imageAlignment(): NSImageAlignment {
        val sel = ObjCRuntime.sel("imageAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageAlignment
    }
    fun setImageAlignment(value: NSImageAlignment) {
        val sel = ObjCRuntime.sel("setImageAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

