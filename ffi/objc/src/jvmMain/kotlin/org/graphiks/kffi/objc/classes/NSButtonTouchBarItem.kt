/**
 * Kotlin/JVM wrapper for Objective-C class: NSButtonTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSButtonTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSButtonTouchBarItem") }
        
        fun buttonTouchBarItemWithIdentifier_title_target_action(identifier: NSTouchBarItemIdentifier, title: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonTouchBarItemWithIdentifier:title:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, title, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun buttonTouchBarItemWithIdentifier_title_target_action(identifier: NSTouchBarItemIdentifier, title: String, target: MemorySegment, action: MemorySegment): MemorySegment = buttonTouchBarItemWithIdentifier_title_target_action(identifier, ObjCRuntime.newNSString(Arena.global(), title), target, action)
        
        fun buttonTouchBarItemWithIdentifier_image_target_action(identifier: NSTouchBarItemIdentifier, image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonTouchBarItemWithIdentifier:image:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, image, target, action) as MemorySegment
        }
        
        fun buttonTouchBarItemWithIdentifier_title_image_target_action(identifier: NSTouchBarItemIdentifier, title: MemorySegment, image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonTouchBarItemWithIdentifier:title:image:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, title, image, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun buttonTouchBarItemWithIdentifier_title_image_target_action(identifier: NSTouchBarItemIdentifier, title: String, image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment = buttonTouchBarItemWithIdentifier_title_image_target_action(identifier, ObjCRuntime.newNSString(Arena.global(), title), image, target, action)
        
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezelColor
    fun bezelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("bezelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBezelColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBezelColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property target
    fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
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
    
    // @property customizationLabel
    fun customizationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun customizationLabelAsString(): String = ObjCRuntime.toJavaString(customizationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCustomizationLabel(value: String) = setCustomizationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
}

