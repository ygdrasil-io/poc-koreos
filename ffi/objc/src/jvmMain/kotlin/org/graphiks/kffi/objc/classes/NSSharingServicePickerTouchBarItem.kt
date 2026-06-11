/**
 * Kotlin/JVM wrapper for Objective-C class: NSSharingServicePickerTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSSharingServicePickerTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSharingServicePickerTouchBarItem") }
        
    }
    
    // @property delegate
    /** @return id<NSSharingServicePickerTouchBarItemDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enabled
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property buttonTitle
    fun buttonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("buttonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setButtonTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setButtonTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun buttonTitleAsString(): String = ObjCRuntime.toJavaString(buttonTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setButtonTitle(value: String) = setButtonTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property buttonImage
    fun buttonImage(): MemorySegment {
        val sel = ObjCRuntime.sel("buttonImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setButtonImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setButtonImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

