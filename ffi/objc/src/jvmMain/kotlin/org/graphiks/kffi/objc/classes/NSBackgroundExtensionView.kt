/**
 * Kotlin/JVM wrapper for Objective-C class: NSBackgroundExtensionView
 * Superclass: NSView
 */
open class NSBackgroundExtensionView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBackgroundExtensionView") }
        
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
    
    // @property automaticallyPlacesContentView
    fun automaticallyPlacesContentView(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyPlacesContentView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyPlacesContentView(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyPlacesContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

