/**
 * Kotlin/JVM wrapper for Objective-C class: NSPageController
 * Superclass: NSViewController
 * Protocols: NSAnimatablePropertyContainer, NSCoding
 */
open class NSPageController(ptr: MemorySegment) : NSViewController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPageController") }
        
    }
    
    fun navigateForwardToObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("navigateForwardToObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun completeTransition(): Unit {
        val sel = ObjCRuntime.sel("completeTransition")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun navigateBack(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("navigateBack:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun navigateForward(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("navigateForward:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeSelectedIndexFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeSelectedIndexFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property delegate
    /** @return id<NSPageControllerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedViewController
    fun selectedViewController(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedViewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property transitionStyle
    fun transitionStyle(): NSPageControllerTransitionStyle {
        val sel = ObjCRuntime.sel("transitionStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPageControllerTransitionStyle
    }
    fun setTransitionStyle(value: NSPageControllerTransitionStyle) {
        val sel = ObjCRuntime.sel("setTransitionStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arrangedObjects
    fun arrangedObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("arrangedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setArrangedObjects(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setArrangedObjects:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedIndex
    fun selectedIndex(): NSInteger {
        val sel = ObjCRuntime.sel("selectedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

