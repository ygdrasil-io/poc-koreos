/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextViewportLayoutController
 * Superclass: NSObject
 */
open class NSTextViewportLayoutController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextViewportLayoutController") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithTextLayoutManager(textLayoutManager: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTextLayoutManager:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textLayoutManager) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun layoutViewport(): Unit {
        val sel = ObjCRuntime.sel("layoutViewport")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun relocateViewportToTextLocation(textLocation: MemorySegment): CGFloat {
        val sel = ObjCRuntime.sel("relocateViewportToTextLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, textLocation) as CGFloat
    }
    
    fun adjustViewportByVerticalOffset(verticalOffset: CGFloat): Unit {
        val sel = ObjCRuntime.sel("adjustViewportByVerticalOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, verticalOffset)
    }
    
    // @property delegate
    /** @return id<NSTextViewportLayoutControllerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textLayoutManager
    fun textLayoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property viewportBounds
    fun viewportBounds(): CGRect {
        val sel = ObjCRuntime.sel("viewportBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property viewportRange
    fun viewportRange(): MemorySegment {
        val sel = ObjCRuntime.sel("viewportRange")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

