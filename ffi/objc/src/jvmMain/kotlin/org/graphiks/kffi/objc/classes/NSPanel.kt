/**
 * Kotlin/JVM wrapper for Objective-C class: NSPanel
 * Superclass: NSWindow
 */
open class NSPanel(ptr: MemorySegment) : NSWindow(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPanel") }
        
    }
    
    // @property floatingPanel
    fun isFloatingPanel(): BOOL {
        val sel = ObjCRuntime.sel("isFloatingPanel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFloatingPanel(value: BOOL) {
        val sel = ObjCRuntime.sel("setFloatingPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property becomesKeyOnlyIfNeeded
    fun becomesKeyOnlyIfNeeded(): BOOL {
        val sel = ObjCRuntime.sel("becomesKeyOnlyIfNeeded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBecomesKeyOnlyIfNeeded(value: BOOL) {
        val sel = ObjCRuntime.sel("setBecomesKeyOnlyIfNeeded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property worksWhenModal
    fun worksWhenModal(): BOOL {
        val sel = ObjCRuntime.sel("worksWhenModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setWorksWhenModal(value: BOOL) {
        val sel = ObjCRuntime.sel("setWorksWhenModal:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

