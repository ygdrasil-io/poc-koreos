/**
 * Kotlin/JVM wrapper for Objective-C class: NSTitlebarAccessoryViewController
 * Superclass: NSViewController
 * Protocols: NSAnimationDelegate, NSAnimatablePropertyContainer
 */
open class NSTitlebarAccessoryViewController(ptr: MemorySegment) : NSViewController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTitlebarAccessoryViewController") }
        
    }
    
    fun viewWillAppear(): Unit {
        val sel = ObjCRuntime.sel("viewWillAppear")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewDidAppear(): Unit {
        val sel = ObjCRuntime.sel("viewDidAppear")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewDidDisappear(): Unit {
        val sel = ObjCRuntime.sel("viewDidDisappear")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property layoutAttribute
    fun layoutAttribute(): NSLayoutAttribute {
        val sel = ObjCRuntime.sel("layoutAttribute")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLayoutAttribute
    }
    fun setLayoutAttribute(value: NSLayoutAttribute) {
        val sel = ObjCRuntime.sel("setLayoutAttribute:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fullScreenMinHeight
    fun fullScreenMinHeight(): CGFloat {
        val sel = ObjCRuntime.sel("fullScreenMinHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setFullScreenMinHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFullScreenMinHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticallyAdjustsSize
    fun automaticallyAdjustsSize(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyAdjustsSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyAdjustsSize(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyAdjustsSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredScrollEdgeEffectStyle
    fun preferredScrollEdgeEffectStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("preferredScrollEdgeEffectStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPreferredScrollEdgeEffectStyle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPreferredScrollEdgeEffectStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

