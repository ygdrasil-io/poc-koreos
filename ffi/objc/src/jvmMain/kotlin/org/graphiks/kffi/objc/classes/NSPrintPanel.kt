/**
 * Kotlin/JVM wrapper for Objective-C class: NSPrintPanel
 * Superclass: NSObject
 */
open class NSPrintPanel(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPrintPanel") }
        
        fun printPanel(): MemorySegment {
            val sel = ObjCRuntime.sel("printPanel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun addAccessoryController(accessoryController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addAccessoryController:")
        ObjCRuntime.msgSend(null, ptr, sel, accessoryController)
    }
    
    fun removeAccessoryController(accessoryController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeAccessoryController:")
        ObjCRuntime.msgSend(null, ptr, sel, accessoryController)
    }
    
    fun setDefaultButtonTitle(defaultButtonTitle: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDefaultButtonTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, defaultButtonTitle)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setDefaultButtonTitle(defaultButtonTitle: String): Unit = setDefaultButtonTitle(ObjCRuntime.newNSString(Arena.global(), defaultButtonTitle))
    
    fun defaultButtonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultButtonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun defaultButtonTitleAsString(): String = ObjCRuntime.toJavaString(defaultButtonTitle())
    
    fun beginSheetUsingPrintInfo_onWindow_completionHandler(printInfo: MemorySegment, parentWindow: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetUsingPrintInfo:onWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, printInfo, parentWindow, handler)
    }
    
    fun beginSheetWithPrintInfo_modalForWindow_delegate_didEndSelector_contextInfo(printInfo: MemorySegment, docWindow: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetWithPrintInfo:modalForWindow:delegate:didEndSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, printInfo, docWindow, delegate, didEndSelector, contextInfo)
    }
    
    fun runModalWithPrintInfo(printInfo: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("runModalWithPrintInfo:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, printInfo) as NSInteger
    }
    
    fun runModal(): NSInteger {
        val sel = ObjCRuntime.sel("runModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property accessoryControllers
    /** @return NSArray<__kindof NSViewController *> * */
    fun accessoryControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property options
    fun options(): NSPrintPanelOptions {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintPanelOptions
    }
    fun setOptions(value: NSPrintPanelOptions) {
        val sel = ObjCRuntime.sel("setOptions:")
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
    
    // @property jobStyleHint
    fun jobStyleHint(): NSPrintPanelJobStyleHint {
        val sel = ObjCRuntime.sel("jobStyleHint")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintPanelJobStyleHint
    }
    fun setJobStyleHint(value: NSPrintPanelJobStyleHint) {
        val sel = ObjCRuntime.sel("setJobStyleHint:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property printInfo
    fun printInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("printInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDeprecated on NSPrintPanel ─────────────────────────────────────────

fun NSPrintPanel.setAccessoryView(accessoryView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAccessoryView:")
    ObjCRuntime.msgSend(null, ptr, sel, accessoryView)
}

fun NSPrintPanel.accessoryView(): MemorySegment {
    val sel = ObjCRuntime.sel("accessoryView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSPrintPanel.updateFromPrintInfo(): Unit {
    val sel = ObjCRuntime.sel("updateFromPrintInfo")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSPrintPanel.finalWritePrintInfo(): Unit {
    val sel = ObjCRuntime.sel("finalWritePrintInfo")
    ObjCRuntime.msgSend(null, ptr, sel)
}

