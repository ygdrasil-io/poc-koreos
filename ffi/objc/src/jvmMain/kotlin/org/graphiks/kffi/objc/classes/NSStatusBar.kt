/**
 * Kotlin/JVM wrapper for Objective-C class: NSStatusBar
 * Superclass: NSObject
 */
open class NSStatusBar(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStatusBar") }
        
        fun systemStatusBar(): MemorySegment {
            val sel = ObjCRuntime.sel("systemStatusBar")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun statusItemWithLength(length: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("statusItemWithLength:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, length) as MemorySegment
    }
    
    fun removeStatusItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeStatusItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    // @property systemStatusBar
    fun systemStatusBar(): MemorySegment {
        val sel = ObjCRuntime.sel("systemStatusBar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property vertical
    fun isVertical(): BOOL {
        val sel = ObjCRuntime.sel("isVertical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property thickness
    fun thickness(): CGFloat {
        val sel = ObjCRuntime.sel("thickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

