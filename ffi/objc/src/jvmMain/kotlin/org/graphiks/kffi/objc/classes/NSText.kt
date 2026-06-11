/**
 * Kotlin/JVM wrapper for Objective-C class: NSText
 * Superclass: NSView
 * Protocols: NSChangeSpelling, NSIgnoreMisspelledWords
 */
open class NSText(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSText") }
        
    }
    
    fun initWithFrame(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun replaceCharactersInRange_withString(range: NSRange, string: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceCharactersInRange:withString:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), string)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun replaceCharactersInRange_withString(range: NSRange, string: String): Unit = replaceCharactersInRange_withString(range, ObjCRuntime.newNSString(Arena.global(), string))
    
    fun replaceCharactersInRange_withRTF(range: NSRange, rtfData: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceCharactersInRange:withRTF:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), rtfData)
    }
    
    fun replaceCharactersInRange_withRTFD(range: NSRange, rtfdData: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceCharactersInRange:withRTFD:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), rtfdData)
    }
    
    fun RTFFromRange(range: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("RTFFromRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    fun RTFDFromRange(range: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("RTFDFromRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    fun writeRTFDToFile_atomically(path: MemorySegment, flag: BOOL): BOOL {
        val sel = ObjCRuntime.sel("writeRTFDToFile:atomically:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, flag) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun writeRTFDToFile_atomically(path: String, flag: BOOL): BOOL = writeRTFDToFile_atomically(ObjCRuntime.newNSString(Arena.global(), path), flag)
    
    fun readRTFDFromFile(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("readRTFDFromFile:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun readRTFDFromFile(path: String): BOOL = readRTFDFromFile(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun scrollRangeToVisible(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("scrollRangeToVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun setTextColor_range(color: MemorySegment, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setTextColor:range:")
        ObjCRuntime.msgSend(null, ptr, sel, color, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun setFont_range(font: MemorySegment, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setFont:range:")
        ObjCRuntime.msgSend(null, ptr, sel, font, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun copy(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("copy:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun copyFont(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("copyFont:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun copyRuler(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("copyRuler:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun cut(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cut:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun delete(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("delete:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun paste(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("paste:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun pasteFont(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("pasteFont:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun pasteRuler(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("pasteRuler:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun changeFont(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeFont:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun alignLeft(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("alignLeft:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun alignRight(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("alignRight:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun alignCenter(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("alignCenter:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun subscript(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("subscript:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun superscript(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("superscript:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun underline(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("underline:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun unscript(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unscript:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun showGuessPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showGuessPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun checkSpelling(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("checkSpelling:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun toggleRuler(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleRuler:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property string
    fun string(): MemorySegment {
        val sel = ObjCRuntime.sel("string")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringAsString(): String = ObjCRuntime.toJavaString(string())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setString(value: String) = setString(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property delegate
    /** @return id<NSTextDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectable
    fun isSelectable(): BOOL {
        val sel = ObjCRuntime.sel("isSelectable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectable(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property richText
    fun isRichText(): BOOL {
        val sel = ObjCRuntime.sel("isRichText")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRichText(value: BOOL) {
        val sel = ObjCRuntime.sel("setRichText:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property importsGraphics
    fun importsGraphics(): BOOL {
        val sel = ObjCRuntime.sel("importsGraphics")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setImportsGraphics(value: BOOL) {
        val sel = ObjCRuntime.sel("setImportsGraphics:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fieldEditor
    fun isFieldEditor(): BOOL {
        val sel = ObjCRuntime.sel("isFieldEditor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFieldEditor(value: BOOL) {
        val sel = ObjCRuntime.sel("setFieldEditor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesFontPanel
    fun usesFontPanel(): BOOL {
        val sel = ObjCRuntime.sel("usesFontPanel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesFontPanel(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesFontPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsBackground
    fun drawsBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsBackground:")
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
    
    // @property rulerVisible
    fun isRulerVisible(): BOOL {
        val sel = ObjCRuntime.sel("isRulerVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property selectedRange
    fun selectedRange(): NSRange {
        val sel = ObjCRuntime.sel("selectedRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    fun setSelectedRange(value: NSRange) {
        val sel = ObjCRuntime.sel("setSelectedRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    // @property font
    fun font(): MemorySegment {
        val sel = ObjCRuntime.sel("font")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textColor
    fun textColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alignment
    fun alignment(): NSTextAlignment {
        val sel = ObjCRuntime.sel("alignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
    }
    fun setAlignment(value: NSTextAlignment) {
        val sel = ObjCRuntime.sel("setAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property baseWritingDirection
    fun baseWritingDirection(): NSWritingDirection {
        val sel = ObjCRuntime.sel("baseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    fun setBaseWritingDirection(value: NSWritingDirection) {
        val sel = ObjCRuntime.sel("setBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property minSize
    fun minSize(): NSSize {
        val sel = ObjCRuntime.sel("minSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property horizontallyResizable
    fun isHorizontallyResizable(): BOOL {
        val sel = ObjCRuntime.sel("isHorizontallyResizable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHorizontallyResizable(value: BOOL) {
        val sel = ObjCRuntime.sel("setHorizontallyResizable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticallyResizable
    fun isVerticallyResizable(): BOOL {
        val sel = ObjCRuntime.sel("isVerticallyResizable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVerticallyResizable(value: BOOL) {
        val sel = ObjCRuntime.sel("setVerticallyResizable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

