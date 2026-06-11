/**
 * Kotlin/JVM wrapper for Objective-C class: NSButton
 * Superclass: NSControl
 * Protocols: NSUserInterfaceValidations, NSAccessibilityButton, NSUserInterfaceCompression
 */
open class NSButton(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSButton") }
        
        fun buttonWithTitle_image_target_action(title: MemorySegment, image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonWithTitle:image:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, image, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun buttonWithTitle_image_target_action(title: String, image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment = buttonWithTitle_image_target_action(ObjCRuntime.newNSString(Arena.global(), title), image, target, action)
        
        fun buttonWithTitle_target_action(title: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonWithTitle:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun buttonWithTitle_target_action(title: String, target: MemorySegment, action: MemorySegment): MemorySegment = buttonWithTitle_target_action(ObjCRuntime.newNSString(Arena.global(), title), target, action)
        
        fun buttonWithImage_target_action(image: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("buttonWithImage:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, image, target, action) as MemorySegment
        }
        
        fun checkboxWithTitle_target_action(title: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("checkboxWithTitle:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun checkboxWithTitle_target_action(title: String, target: MemorySegment, action: MemorySegment): MemorySegment = checkboxWithTitle_target_action(ObjCRuntime.newNSString(Arena.global(), title), target, action)
        
        fun radioButtonWithTitle_target_action(title: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("radioButtonWithTitle:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun radioButtonWithTitle_target_action(title: String, target: MemorySegment, action: MemorySegment): MemorySegment = radioButtonWithTitle_target_action(ObjCRuntime.newNSString(Arena.global(), title), target, action)
        
    }
    
    fun setButtonType(type: NSButtonType): Unit {
        val sel = ObjCRuntime.sel("setButtonType:")
        ObjCRuntime.msgSend(null, ptr, sel, type)
    }
    
    fun setPeriodicDelay_interval(delay: Float, interval: Float): Unit {
        val sel = ObjCRuntime.sel("setPeriodicDelay:interval:")
        ObjCRuntime.msgSend(null, ptr, sel, delay, interval)
    }
    
    fun getPeriodicDelay_interval(delay: MemorySegment, interval: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getPeriodicDelay:interval:")
        ObjCRuntime.msgSend(null, ptr, sel, delay, interval)
    }
    
    fun setNextState(): Unit {
        val sel = ObjCRuntime.sel("setNextState")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun highlight(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("highlight:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun performKeyEquivalent(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performKeyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    fun compressWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("compressWithPrioritizedCompressionOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, prioritizedOptions)
    }
    
    fun minimumSizeWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment): NSSize {
        val sel = ObjCRuntime.sel("minimumSizeWithPrioritizedCompressionOptions:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, prioritizedOptions) as NSSize
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
    
    // @property attributedTitle
    fun attributedTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alternateTitle
    fun alternateTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("alternateTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAlternateTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAlternateTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun alternateTitleAsString(): String = ObjCRuntime.toJavaString(alternateTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setAlternateTitle(value: String) = setAlternateTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property attributedAlternateTitle
    fun attributedAlternateTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedAlternateTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedAlternateTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedAlternateTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasDestructiveAction
    fun hasDestructiveAction(): BOOL {
        val sel = ObjCRuntime.sel("hasDestructiveAction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasDestructiveAction(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasDestructiveAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sound
    fun sound(): MemorySegment {
        val sel = ObjCRuntime.sel("sound")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSound(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSound:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property springLoaded
    fun isSpringLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isSpringLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSpringLoaded(value: BOOL) {
        val sel = ObjCRuntime.sel("setSpringLoaded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxAcceleratorLevel
    fun maxAcceleratorLevel(): NSInteger {
        val sel = ObjCRuntime.sel("maxAcceleratorLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaxAcceleratorLevel(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaxAcceleratorLevel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezelStyle
    fun bezelStyle(): NSBezelStyle {
        val sel = ObjCRuntime.sel("bezelStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBezelStyle
    }
    fun setBezelStyle(value: NSBezelStyle) {
        val sel = ObjCRuntime.sel("setBezelStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bordered
    fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property transparent
    fun isTransparent(): BOOL {
        val sel = ObjCRuntime.sel("isTransparent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTransparent(value: BOOL) {
        val sel = ObjCRuntime.sel("setTransparent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsBorderOnlyWhileMouseInside
    fun showsBorderOnlyWhileMouseInside(): BOOL {
        val sel = ObjCRuntime.sel("showsBorderOnlyWhileMouseInside")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsBorderOnlyWhileMouseInside(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsBorderOnlyWhileMouseInside:")
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
    
    // @property contentTintColor
    fun contentTintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("contentTintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentTintColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentTintColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tintProminence
    fun tintProminence(): NSTintProminence {
        val sel = ObjCRuntime.sel("tintProminence")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTintProminence
    }
    fun setTintProminence(value: NSTintProminence) {
        val sel = ObjCRuntime.sel("setTintProminence:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alternateImage
    fun alternateImage(): MemorySegment {
        val sel = ObjCRuntime.sel("alternateImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAlternateImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAlternateImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imagePosition
    fun imagePosition(): NSCellImagePosition {
        val sel = ObjCRuntime.sel("imagePosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCellImagePosition
    }
    fun setImagePosition(value: NSCellImagePosition) {
        val sel = ObjCRuntime.sel("setImagePosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageScaling
    fun imageScaling(): NSImageScaling {
        val sel = ObjCRuntime.sel("imageScaling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageScaling
    }
    fun setImageScaling(value: NSImageScaling) {
        val sel = ObjCRuntime.sel("setImageScaling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageHugsTitle
    fun imageHugsTitle(): BOOL {
        val sel = ObjCRuntime.sel("imageHugsTitle")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setImageHugsTitle(value: BOOL) {
        val sel = ObjCRuntime.sel("setImageHugsTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property symbolConfiguration
    fun symbolConfiguration(): MemorySegment {
        val sel = ObjCRuntime.sel("symbolConfiguration")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSymbolConfiguration(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSymbolConfiguration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    fun state(): NSControlStateValue {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSControlStateValue
    }
    fun setState(value: NSControlStateValue) {
        val sel = ObjCRuntime.sel("setState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsMixedState
    fun allowsMixedState(): BOOL {
        val sel = ObjCRuntime.sel("allowsMixedState")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsMixedState(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsMixedState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyEquivalent
    fun keyEquivalent(): MemorySegment {
        val sel = ObjCRuntime.sel("keyEquivalent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setKeyEquivalent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setKeyEquivalent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun keyEquivalentAsString(): String = ObjCRuntime.toJavaString(keyEquivalent())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setKeyEquivalent(value: String) = setKeyEquivalent(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property keyEquivalentModifierMask
    fun keyEquivalentModifierMask(): NSEventModifierFlags {
        val sel = ObjCRuntime.sel("keyEquivalentModifierMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventModifierFlags
    }
    fun setKeyEquivalentModifierMask(value: NSEventModifierFlags) {
        val sel = ObjCRuntime.sel("setKeyEquivalentModifierMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property activeCompressionOptions
    fun activeCompressionOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("activeCompressionOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property borderShape
    fun borderShape(): NSControlBorderShape {
        val sel = ObjCRuntime.sel("borderShape")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlBorderShape
    }
    fun setBorderShape(value: NSControlBorderShape) {
        val sel = ObjCRuntime.sel("setBorderShape:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSButtonDeprecated on NSButton ─────────────────────────────────────────

fun NSButton.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

