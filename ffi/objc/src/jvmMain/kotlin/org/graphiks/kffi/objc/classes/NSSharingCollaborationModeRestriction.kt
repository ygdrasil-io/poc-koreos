/**
 * Kotlin/JVM wrapper for Objective-C class: NSSharingCollaborationModeRestriction
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSSharingCollaborationModeRestriction(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSharingCollaborationModeRestriction") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithDisabledMode(disabledMode: NSSharingCollaborationMode): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDisabledMode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, disabledMode) as MemorySegment
    }
    
    fun initWithDisabledMode_alertTitle_alertMessage(disabledMode: NSSharingCollaborationMode, alertTitle: MemorySegment, alertMessage: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDisabledMode:alertTitle:alertMessage:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, disabledMode, alertTitle, alertMessage) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithDisabledMode_alertTitle_alertMessage(disabledMode: NSSharingCollaborationMode, alertTitle: String, alertMessage: String): MemorySegment = initWithDisabledMode_alertTitle_alertMessage(disabledMode, ObjCRuntime.newNSString(Arena.global(), alertTitle), ObjCRuntime.newNSString(Arena.global(), alertMessage))
    
    fun initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle(disabledMode: NSSharingCollaborationMode, alertTitle: MemorySegment, alertMessage: MemorySegment, alertDismissButtonTitle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDisabledMode:alertTitle:alertMessage:alertDismissButtonTitle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, disabledMode, alertTitle, alertMessage, alertDismissButtonTitle) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle(disabledMode: NSSharingCollaborationMode, alertTitle: String, alertMessage: String, alertDismissButtonTitle: String): MemorySegment = initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle(disabledMode, ObjCRuntime.newNSString(Arena.global(), alertTitle), ObjCRuntime.newNSString(Arena.global(), alertMessage), ObjCRuntime.newNSString(Arena.global(), alertDismissButtonTitle))
    
    fun initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle_alertRecoverySuggestionButtonTitle_alertRecoverySuggestionButtonLaunchURL(disabledMode: NSSharingCollaborationMode, alertTitle: MemorySegment, alertMessage: MemorySegment, alertDismissButtonTitle: MemorySegment, alertRecoverySuggestionButtonTitle: MemorySegment, alertRecoverySuggestionButtonLaunchURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDisabledMode:alertTitle:alertMessage:alertDismissButtonTitle:alertRecoverySuggestionButtonTitle:alertRecoverySuggestionButtonLaunchURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, disabledMode, alertTitle, alertMessage, alertDismissButtonTitle, alertRecoverySuggestionButtonTitle, alertRecoverySuggestionButtonLaunchURL) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle_alertRecoverySuggestionButtonTitle_alertRecoverySuggestionButtonLaunchURL(disabledMode: NSSharingCollaborationMode, alertTitle: String, alertMessage: String, alertDismissButtonTitle: String, alertRecoverySuggestionButtonTitle: String, alertRecoverySuggestionButtonLaunchURL: MemorySegment): MemorySegment = initWithDisabledMode_alertTitle_alertMessage_alertDismissButtonTitle_alertRecoverySuggestionButtonTitle_alertRecoverySuggestionButtonLaunchURL(disabledMode, ObjCRuntime.newNSString(Arena.global(), alertTitle), ObjCRuntime.newNSString(Arena.global(), alertMessage), ObjCRuntime.newNSString(Arena.global(), alertDismissButtonTitle), ObjCRuntime.newNSString(Arena.global(), alertRecoverySuggestionButtonTitle), alertRecoverySuggestionButtonLaunchURL)
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property disabledMode
    fun disabledMode(): NSSharingCollaborationMode {
        val sel = ObjCRuntime.sel("disabledMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSharingCollaborationMode
    }
    
    // @property alertTitle
    fun alertTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("alertTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun alertTitleAsString(): String = ObjCRuntime.toJavaString(alertTitle())
    
    // @property alertMessage
    fun alertMessage(): MemorySegment {
        val sel = ObjCRuntime.sel("alertMessage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun alertMessageAsString(): String = ObjCRuntime.toJavaString(alertMessage())
    
    // @property alertDismissButtonTitle
    fun alertDismissButtonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("alertDismissButtonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun alertDismissButtonTitleAsString(): String = ObjCRuntime.toJavaString(alertDismissButtonTitle())
    
    // @property alertRecoverySuggestionButtonTitle
    fun alertRecoverySuggestionButtonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("alertRecoverySuggestionButtonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun alertRecoverySuggestionButtonTitleAsString(): String = ObjCRuntime.toJavaString(alertRecoverySuggestionButtonTitle())
    
    // @property alertRecoverySuggestionButtonLaunchURL
    fun alertRecoverySuggestionButtonLaunchURL(): MemorySegment {
        val sel = ObjCRuntime.sel("alertRecoverySuggestionButtonLaunchURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

