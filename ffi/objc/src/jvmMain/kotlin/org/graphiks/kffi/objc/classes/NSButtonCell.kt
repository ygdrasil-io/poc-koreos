/**
 * Kotlin/JVM wrapper for Objective-C class: NSButtonCell
 * Superclass: NSActionCell
 */
open class NSButtonCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSButtonCell") }
        
    }
    
    fun initTextCell(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initTextCell(string: String): MemorySegment = initTextCell(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun initImageCell(image: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initImageCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, image) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
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
    
    fun performClick(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performClick:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun mouseEntered(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseEntered:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseExited(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseExited:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun drawBezelWithFrame_inView(frame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawBezelWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawImage_withFrame_inView(image: MemorySegment, frame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawImage:withFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, image, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawTitle_withFrame_inView(title: MemorySegment, frame: NSRect, controlView: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("drawTitle:withFrame:inView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, title, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView) as NSRect
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
    
    // @property highlightsBy
    fun highlightsBy(): NSCellStyleMask {
        val sel = ObjCRuntime.sel("highlightsBy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCellStyleMask
    }
    fun setHighlightsBy(value: NSCellStyleMask) {
        val sel = ObjCRuntime.sel("setHighlightsBy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsStateBy
    fun showsStateBy(): NSCellStyleMask {
        val sel = ObjCRuntime.sel("showsStateBy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCellStyleMask
    }
    fun setShowsStateBy(value: NSCellStyleMask) {
        val sel = ObjCRuntime.sel("setShowsStateBy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property transparent
    fun isTransparent(): BOOL {
        val sel = ObjCRuntime.sel("isTransparent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTransparent(value: BOOL) {
        val sel = ObjCRuntime.sel("setTransparent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opaque
    fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property imageDimsWhenDisabled
    fun imageDimsWhenDisabled(): BOOL {
        val sel = ObjCRuntime.sel("imageDimsWhenDisabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setImageDimsWhenDisabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setImageDimsWhenDisabled:")
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
    
    // @property sound
    fun sound(): MemorySegment {
        val sel = ObjCRuntime.sel("sound")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSound(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSound:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSButtonCell ─────────────────────────────────────────

fun NSButtonCell.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

fun NSButtonCell.setAlternateTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAlternateTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

fun NSButtonCell.setAlternateMnemonicLocation(location: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setAlternateMnemonicLocation:")
    ObjCRuntime.msgSend(null, ptr, sel, location)
}

fun NSButtonCell.alternateMnemonicLocation(): NSUInteger {
    val sel = ObjCRuntime.sel("alternateMnemonicLocation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSButtonCell.alternateMnemonic(): MemorySegment {
    val sel = ObjCRuntime.sel("alternateMnemonic")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSButtonCell.setKeyEquivalentFont_size(fontName: MemorySegment, fontSize: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setKeyEquivalentFont:size:")
    ObjCRuntime.msgSend(null, ptr, sel, fontName, fontSize)
}

fun NSButtonCell.gradientType(): NSGradientType {
    val sel = ObjCRuntime.sel("gradientType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGradientType
}

fun NSButtonCell.setGradientType(gradientType: NSGradientType): Unit {
    val sel = ObjCRuntime.sel("setGradientType:")
    ObjCRuntime.msgSend(null, ptr, sel, gradientType)
}

fun NSButtonCell.keyEquivalentFont(): MemorySegment {
    val sel = ObjCRuntime.sel("keyEquivalentFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSButtonCell.setKeyEquivalentFont(keyEquivalentFont: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setKeyEquivalentFont:")
    ObjCRuntime.msgSend(null, ptr, sel, keyEquivalentFont)
}

// @property gradientType
fun NSButtonCell.gradientType(): NSGradientType {
    val sel = ObjCRuntime.sel("gradientType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGradientType
}
fun NSButtonCell.setGradientType(value: NSGradientType) {
    val sel = ObjCRuntime.sel("setGradientType:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property keyEquivalentFont
fun NSButtonCell.keyEquivalentFont(): MemorySegment {
    val sel = ObjCRuntime.sel("keyEquivalentFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSButtonCell.setKeyEquivalentFont(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setKeyEquivalentFont:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

