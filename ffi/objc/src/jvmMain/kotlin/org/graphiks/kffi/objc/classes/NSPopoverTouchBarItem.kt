/**
 * Kotlin/JVM wrapper for Objective-C class: NSPopoverTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSPopoverTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPopoverTouchBarItem") }
        
    }
    
    fun showPopover(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showPopover:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun dismissPopover(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("dismissPopover:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun makeStandardActivatePopoverGestureRecognizer(): MemorySegment {
        val sel = ObjCRuntime.sel("makeStandardActivatePopoverGestureRecognizer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property popoverTouchBar
    fun popoverTouchBar(): MemorySegment {
        val sel = ObjCRuntime.sel("popoverTouchBar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPopoverTouchBar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPopoverTouchBar:")
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
    
    // @property collapsedRepresentation
    fun collapsedRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("collapsedRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollapsedRepresentation(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollapsedRepresentation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsedRepresentationImage
    fun collapsedRepresentationImage(): MemorySegment {
        val sel = ObjCRuntime.sel("collapsedRepresentationImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollapsedRepresentationImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollapsedRepresentationImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsedRepresentationLabel
    fun collapsedRepresentationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("collapsedRepresentationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollapsedRepresentationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollapsedRepresentationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun collapsedRepresentationLabelAsString(): String = ObjCRuntime.toJavaString(collapsedRepresentationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCollapsedRepresentationLabel(value: String) = setCollapsedRepresentationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property pressAndHoldTouchBar
    fun pressAndHoldTouchBar(): MemorySegment {
        val sel = ObjCRuntime.sel("pressAndHoldTouchBar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPressAndHoldTouchBar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPressAndHoldTouchBar:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsCloseButton
    fun showsCloseButton(): BOOL {
        val sel = ObjCRuntime.sel("showsCloseButton")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsCloseButton(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsCloseButton:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

