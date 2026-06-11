/**
 * Kotlin/JVM wrapper for Objective-C class: NSBox
 * Superclass: NSView
 */
open class NSBox(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBox") }
        
    }
    
    fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setFrameFromContentFrame(contentFrame: NSRect): Unit {
        val sel = ObjCRuntime.sel("setFrameFromContentFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(contentFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property boxType
    fun boxType(): NSBoxType {
        val sel = ObjCRuntime.sel("boxType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBoxType
    }
    fun setBoxType(value: NSBoxType) {
        val sel = ObjCRuntime.sel("setBoxType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titlePosition
    fun titlePosition(): NSTitlePosition {
        val sel = ObjCRuntime.sel("titlePosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTitlePosition
    }
    fun setTitlePosition(value: NSTitlePosition) {
        val sel = ObjCRuntime.sel("setTitlePosition:")
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
    
    // @property titleFont
    fun titleFont(): MemorySegment {
        val sel = ObjCRuntime.sel("titleFont")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitleFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitleFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property borderRect
    fun borderRect(): NSRect {
        val sel = ObjCRuntime.sel("borderRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property titleRect
    fun titleRect(): NSRect {
        val sel = ObjCRuntime.sel("titleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property titleCell
    fun titleCell(): MemorySegment {
        val sel = ObjCRuntime.sel("titleCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property contentViewMargins
    fun contentViewMargins(): NSSize {
        val sel = ObjCRuntime.sel("contentViewMargins")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentViewMargins(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentViewMargins:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
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
    
    // @property transparent
    fun isTransparent(): BOOL {
        val sel = ObjCRuntime.sel("isTransparent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTransparent(value: BOOL) {
        val sel = ObjCRuntime.sel("setTransparent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property borderWidth
    fun borderWidth(): CGFloat {
        val sel = ObjCRuntime.sel("borderWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setBorderWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBorderWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cornerRadius
    fun cornerRadius(): CGFloat {
        val sel = ObjCRuntime.sel("cornerRadius")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setCornerRadius(value: CGFloat) {
        val sel = ObjCRuntime.sel("setCornerRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property borderColor
    fun borderColor(): MemorySegment {
        val sel = ObjCRuntime.sel("borderColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBorderColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBorderColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fillColor
    fun fillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("fillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFillColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFillColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSBox ─────────────────────────────────────────

fun NSBox.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

fun NSBox.borderType(): NSBorderType {
    val sel = ObjCRuntime.sel("borderType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBorderType
}

fun NSBox.setBorderType(borderType: NSBorderType): Unit {
    val sel = ObjCRuntime.sel("setBorderType:")
    ObjCRuntime.msgSend(null, ptr, sel, borderType)
}

// @property borderType
fun NSBox.borderType(): NSBorderType {
    val sel = ObjCRuntime.sel("borderType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBorderType
}
fun NSBox.setBorderType(value: NSBorderType) {
    val sel = ObjCRuntime.sel("setBorderType:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

