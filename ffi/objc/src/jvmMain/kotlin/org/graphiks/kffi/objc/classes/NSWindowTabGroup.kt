/**
 * Kotlin/JVM wrapper for Objective-C class: NSWindowTabGroup
 * Superclass: NSObject
 */
open class NSWindowTabGroup(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWindowTabGroup") }
        
    }
    
    fun addWindow(window: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, window)
    }
    
    fun insertWindow_atIndex(window: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertWindow:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, window, index)
    }
    
    fun removeWindow(window: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, window)
    }
    
    // @property identifier
    fun identifier(): NSWindowTabbingIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowTabbingIdentifier
    }
    
    // @property windows
    /** @return NSArray<NSWindow *> * */
    fun windows(): MemorySegment {
        val sel = ObjCRuntime.sel("windows")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property overviewVisible
    fun isOverviewVisible(): BOOL {
        val sel = ObjCRuntime.sel("isOverviewVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setOverviewVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setOverviewVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabBarVisible
    fun isTabBarVisible(): BOOL {
        val sel = ObjCRuntime.sel("isTabBarVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property selectedWindow
    fun selectedWindow(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedWindow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectedWindow(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectedWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

