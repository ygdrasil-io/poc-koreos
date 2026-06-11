/**
 * Kotlin/JVM wrapper for Objective-C class: NSToolbarItem
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSToolbarItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSToolbarItem") }
        
    }
    
    fun initWithItemIdentifier(itemIdentifier: NSToolbarItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("initWithItemIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemIdentifier) as MemorySegment
    }
    
    fun validate(): Unit {
        val sel = ObjCRuntime.sel("validate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property itemIdentifier
    fun itemIdentifier(): NSToolbarItemIdentifier {
        val sel = ObjCRuntime.sel("itemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemIdentifier
    }
    
    // @property toolbar
    fun toolbar(): MemorySegment {
        val sel = ObjCRuntime.sel("toolbar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property label
    fun label(): MemorySegment {
        val sel = ObjCRuntime.sel("label")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun labelAsString(): String = ObjCRuntime.toJavaString(label())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLabel(value: String) = setLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property paletteLabel
    fun paletteLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("paletteLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPaletteLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPaletteLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun paletteLabelAsString(): String = ObjCRuntime.toJavaString(paletteLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPaletteLabel(value: String) = setPaletteLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property possibleLabels
    /** @return NSSet<NSString *> * */
    fun possibleLabels(): MemorySegment {
        val sel = ObjCRuntime.sel("possibleLabels")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPossibleLabels(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPossibleLabels:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property toolTip
    fun toolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("toolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setToolTip(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun toolTipAsString(): String = ObjCRuntime.toJavaString(toolTip())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setToolTip(value: String) = setToolTip(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property menuFormRepresentation
    fun menuFormRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("menuFormRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenuFormRepresentation(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenuFormRepresentation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tag
    fun tag(): NSInteger {
        val sel = ObjCRuntime.sel("tag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setTag(value: NSInteger) {
        val sel = ObjCRuntime.sel("setTag:")
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
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
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
    
    // @property bordered
    fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundTintColor
    fun backgroundTintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundTintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundTintColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundTintColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property style
    fun style(): NSToolbarItemStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemStyle
    }
    fun setStyle(value: NSToolbarItemStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property navigational
    fun isNavigational(): BOOL {
        val sel = ObjCRuntime.sel("isNavigational")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNavigational(value: BOOL) {
        val sel = ObjCRuntime.sel("setNavigational:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property view
    fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minSize
    fun minSize(): NSSize {
        val sel = ObjCRuntime.sel("minSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property maxSize
    fun maxSize(): NSSize {
        val sel = ObjCRuntime.sel("maxSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMaxSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMaxSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property visibilityPriority
    fun visibilityPriority(): NSToolbarItemVisibilityPriority {
        val sel = ObjCRuntime.sel("visibilityPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSToolbarItemVisibilityPriority
    }
    fun setVisibilityPriority(value: NSToolbarItemVisibilityPriority) {
        val sel = ObjCRuntime.sel("setVisibilityPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property badge
    fun badge(): MemorySegment {
        val sel = ObjCRuntime.sel("badge")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBadge(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBadge:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autovalidates
    fun autovalidates(): BOOL {
        val sel = ObjCRuntime.sel("autovalidates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutovalidates(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutovalidates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsDuplicatesInToolbar
    fun allowsDuplicatesInToolbar(): BOOL {
        val sel = ObjCRuntime.sel("allowsDuplicatesInToolbar")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category:  on NSToolbarItem ─────────────────────────────────────────

