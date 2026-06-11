/**
 * Kotlin/JVM wrapper for Objective-C class: NSTouchBarItem
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSTouchBarItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTouchBarItem") }
        
    }
    
    fun initWithIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property identifier
    fun identifier(): NSTouchBarItemIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarItemIdentifier
    }
    
    // @property visibilityPriority
    fun visibilityPriority(): NSTouchBarItemPriority {
        val sel = ObjCRuntime.sel("visibilityPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSTouchBarItemPriority
    }
    fun setVisibilityPriority(value: NSTouchBarItemPriority) {
        val sel = ObjCRuntime.sel("setVisibilityPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property view
    fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property viewController
    fun viewController(): MemorySegment {
        val sel = ObjCRuntime.sel("viewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property customizationLabel
    fun customizationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun customizationLabelAsString(): String = ObjCRuntime.toJavaString(customizationLabel())
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

