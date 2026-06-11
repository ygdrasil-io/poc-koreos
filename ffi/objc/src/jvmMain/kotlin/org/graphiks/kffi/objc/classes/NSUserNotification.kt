/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserNotification
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSUserNotification(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserNotification") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property subtitle
    fun subtitle(): MemorySegment {
        val sel = ObjCRuntime.sel("subtitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubtitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubtitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun subtitleAsString(): String = ObjCRuntime.toJavaString(subtitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSubtitle(value: String) = setSubtitle(ObjCRuntime.newNSString(Arena.global(), value))
    
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
    
    // @property actionButtonTitle
    fun actionButtonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("actionButtonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setActionButtonTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setActionButtonTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun actionButtonTitleAsString(): String = ObjCRuntime.toJavaString(actionButtonTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setActionButtonTitle(value: String) = setActionButtonTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property userInfo
    /** @return NSDictionary<NSString *,id> * */
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUserInfo(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUserInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property deliveryDate
    fun deliveryDate(): MemorySegment {
        val sel = ObjCRuntime.sel("deliveryDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDeliveryDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDeliveryDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property deliveryTimeZone
    fun deliveryTimeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("deliveryTimeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDeliveryTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDeliveryTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property deliveryRepeatInterval
    fun deliveryRepeatInterval(): MemorySegment {
        val sel = ObjCRuntime.sel("deliveryRepeatInterval")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDeliveryRepeatInterval(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDeliveryRepeatInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property actualDeliveryDate
    fun actualDeliveryDate(): MemorySegment {
        val sel = ObjCRuntime.sel("actualDeliveryDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property presented
    fun isPresented(): BOOL {
        val sel = ObjCRuntime.sel("isPresented")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property remote
    fun isRemote(): BOOL {
        val sel = ObjCRuntime.sel("isRemote")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property soundName
    fun soundName(): MemorySegment {
        val sel = ObjCRuntime.sel("soundName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSoundName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSoundName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun soundNameAsString(): String = ObjCRuntime.toJavaString(soundName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSoundName(value: String) = setSoundName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property hasActionButton
    fun hasActionButton(): BOOL {
        val sel = ObjCRuntime.sel("hasActionButton")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasActionButton(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasActionButton:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property activationType
    fun activationType(): NSUserNotificationActivationType {
        val sel = ObjCRuntime.sel("activationType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserNotificationActivationType
    }
    
    // @property otherButtonTitle
    fun otherButtonTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("otherButtonTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOtherButtonTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOtherButtonTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun otherButtonTitleAsString(): String = ObjCRuntime.toJavaString(otherButtonTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setOtherButtonTitle(value: String) = setOtherButtonTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property identifier
    fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setIdentifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun identifierAsString(): String = ObjCRuntime.toJavaString(identifier())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setIdentifier(value: String) = setIdentifier(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property contentImage
    fun contentImage(): MemorySegment {
        val sel = ObjCRuntime.sel("contentImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasReplyButton
    fun hasReplyButton(): BOOL {
        val sel = ObjCRuntime.sel("hasReplyButton")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasReplyButton(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasReplyButton:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property responsePlaceholder
    fun responsePlaceholder(): MemorySegment {
        val sel = ObjCRuntime.sel("responsePlaceholder")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setResponsePlaceholder(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setResponsePlaceholder:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun responsePlaceholderAsString(): String = ObjCRuntime.toJavaString(responsePlaceholder())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setResponsePlaceholder(value: String) = setResponsePlaceholder(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property response
    fun response(): MemorySegment {
        val sel = ObjCRuntime.sel("response")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property additionalActions
    /** @return NSArray<NSUserNotificationAction *> * */
    fun additionalActions(): MemorySegment {
        val sel = ObjCRuntime.sel("additionalActions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAdditionalActions(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAdditionalActions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property additionalActivationAction
    fun additionalActivationAction(): MemorySegment {
        val sel = ObjCRuntime.sel("additionalActivationAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

