/**
 * Kotlin/JVM wrapper for Objective-C class: NSAlert
 * Superclass: NSObject
 */
open class NSAlert(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAlert") }
        
        fun alertWithError(error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("alertWithError:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, error) as MemorySegment
        }
        
    }
    
    fun addButtonWithTitle(title: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addButtonWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addButtonWithTitle(title: String): MemorySegment = addButtonWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun layout(): Unit {
        val sel = ObjCRuntime.sel("layout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun runModal(): NSModalResponse {
        val sel = ObjCRuntime.sel("runModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSModalResponse
    }
    
    fun beginSheetModalForWindow_completionHandler(sheetWindow: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetModalForWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, sheetWindow, handler)
    }
    
    // @property messageText
    fun messageText(): MemorySegment {
        val sel = ObjCRuntime.sel("messageText")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMessageText(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMessageText:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun messageTextAsString(): String = ObjCRuntime.toJavaString(messageText())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMessageText(value: String) = setMessageText(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property informativeText
    fun informativeText(): MemorySegment {
        val sel = ObjCRuntime.sel("informativeText")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInformativeText(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInformativeText:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun informativeTextAsString(): String = ObjCRuntime.toJavaString(informativeText())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setInformativeText(value: String) = setInformativeText(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property icon
    fun icon(): MemorySegment {
        val sel = ObjCRuntime.sel("icon")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setIcon(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setIcon:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property buttons
    /** @return NSArray<NSButton *> * */
    fun buttons(): MemorySegment {
        val sel = ObjCRuntime.sel("buttons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alertStyle
    fun alertStyle(): NSAlertStyle {
        val sel = ObjCRuntime.sel("alertStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAlertStyle
    }
    fun setAlertStyle(value: NSAlertStyle) {
        val sel = ObjCRuntime.sel("setAlertStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsHelp
    fun showsHelp(): BOOL {
        val sel = ObjCRuntime.sel("showsHelp")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsHelp(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsHelp:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property helpAnchor
    fun helpAnchor(): NSHelpAnchorName {
        val sel = ObjCRuntime.sel("helpAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSHelpAnchorName
    }
    fun setHelpAnchor(value: NSHelpAnchorName) {
        val sel = ObjCRuntime.sel("setHelpAnchor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSAlertDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property accessoryView
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsSuppressionButton
    fun showsSuppressionButton(): BOOL {
        val sel = ObjCRuntime.sel("showsSuppressionButton")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsSuppressionButton(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsSuppressionButton:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property suppressionButton
    fun suppressionButton(): MemorySegment {
        val sel = ObjCRuntime.sel("suppressionButton")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property window
    fun window(): MemorySegment {
        val sel = ObjCRuntime.sel("window")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSAlertDeprecated on NSAlert ─────────────────────────────────────────

fun NSAlert.beginSheetModalForWindow_modalDelegate_didEndSelector_contextInfo(window: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, window, delegate, didEndSelector, contextInfo)
}

// Class method: +[NSAlert alertWithMessageText:defaultButton:alternateButton:otherButton:informativeTextWithFormat:]
fun NSAlert_alertWithMessageText_defaultButton_alternateButton_otherButton_informativeTextWithFormat(message: MemorySegment, defaultButton: MemorySegment, alternateButton: MemorySegment, otherButton: MemorySegment, format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("alertWithMessageText:defaultButton:alternateButton:otherButton:informativeTextWithFormat:")
    val cls = ObjCRuntime.getClass("NSAlert")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, message, defaultButton, alternateButton, otherButton, format) as MemorySegment
}

