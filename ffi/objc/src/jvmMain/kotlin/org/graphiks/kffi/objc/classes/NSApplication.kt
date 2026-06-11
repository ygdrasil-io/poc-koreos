/**
 * Kotlin/JVM wrapper for Objective-C class: NSApplication
 * Superclass: NSResponder
 * Protocols: NSUserInterfaceValidations, NSMenuItemValidation, NSAccessibilityElement, NSAccessibility
 */
open class NSApplication(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSApplication") }
        
        fun detachDrawingThread_toTarget_withObject(selector: MemorySegment, target: MemorySegment, argument: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("detachDrawingThread:toTarget:withObject:")
            ObjCRuntime.msgSend(null, _class, sel, selector, target, argument)
        }
        
        fun sharedApplication(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedApplication")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun hide(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("hide:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun unhide(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unhide:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun unhideWithoutActivation(): Unit {
        val sel = ObjCRuntime.sel("unhideWithoutActivation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun windowWithWindowNumber(windowNum: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("windowWithWindowNumber:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, windowNum) as MemorySegment
    }
    
    fun deactivate(): Unit {
        val sel = ObjCRuntime.sel("deactivate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun activateIgnoringOtherApps(ignoreOtherApps: BOOL): Unit {
        val sel = ObjCRuntime.sel("activateIgnoringOtherApps:")
        ObjCRuntime.msgSend(null, ptr, sel, ignoreOtherApps)
    }
    
    fun activate(): Unit {
        val sel = ObjCRuntime.sel("activate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun yieldActivationToApplication(application: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("yieldActivationToApplication:")
        ObjCRuntime.msgSend(null, ptr, sel, application)
    }
    
    fun yieldActivationToApplicationWithBundleIdentifier(bundleIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("yieldActivationToApplicationWithBundleIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, bundleIdentifier)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun yieldActivationToApplicationWithBundleIdentifier(bundleIdentifier: String): Unit = yieldActivationToApplicationWithBundleIdentifier(ObjCRuntime.newNSString(Arena.global(), bundleIdentifier))
    
    fun hideOtherApplications(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("hideOtherApplications:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun unhideAllApplications(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unhideAllApplications:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun finishLaunching(): Unit {
        val sel = ObjCRuntime.sel("finishLaunching")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun run(): Unit {
        val sel = ObjCRuntime.sel("run")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun runModalForWindow(window: MemorySegment): NSModalResponse {
        val sel = ObjCRuntime.sel("runModalForWindow:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, window) as NSModalResponse
    }
    
    fun stop(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("stop:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun stopModal(): Unit {
        val sel = ObjCRuntime.sel("stopModal")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stopModalWithCode(returnCode: NSModalResponse): Unit {
        val sel = ObjCRuntime.sel("stopModalWithCode:")
        ObjCRuntime.msgSend(null, ptr, sel, returnCode)
    }
    
    fun abortModal(): Unit {
        val sel = ObjCRuntime.sel("abortModal")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun beginModalSessionForWindow(window: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("beginModalSessionForWindow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, window) as MemorySegment
    }
    
    fun runModalSession(session: MemorySegment): NSModalResponse {
        val sel = ObjCRuntime.sel("runModalSession:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, session) as NSModalResponse
    }
    
    fun endModalSession(session: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("endModalSession:")
        ObjCRuntime.msgSend(null, ptr, sel, session)
    }
    
    fun terminate(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("terminate:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun requestUserAttention(requestType: NSRequestUserAttentionType): NSInteger {
        val sel = ObjCRuntime.sel("requestUserAttention:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, requestType) as NSInteger
    }
    
    fun cancelUserAttentionRequest(request: NSInteger): Unit {
        val sel = ObjCRuntime.sel("cancelUserAttentionRequest:")
        ObjCRuntime.msgSend(null, ptr, sel, request)
    }
    
    fun enumerateWindowsWithOptions_usingBlock(options: NSWindowListOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateWindowsWithOptions:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, options, block)
    }
    
    fun preventWindowOrdering(): Unit {
        val sel = ObjCRuntime.sel("preventWindowOrdering")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setWindowsNeedUpdate(needUpdate: BOOL): Unit {
        val sel = ObjCRuntime.sel("setWindowsNeedUpdate:")
        ObjCRuntime.msgSend(null, ptr, sel, needUpdate)
    }
    
    fun updateWindows(): Unit {
        val sel = ObjCRuntime.sel("updateWindows")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun activationPolicy(): NSApplicationActivationPolicy {
        val sel = ObjCRuntime.sel("activationPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSApplicationActivationPolicy
    }
    
    fun setActivationPolicy(activationPolicy: NSApplicationActivationPolicy): BOOL {
        val sel = ObjCRuntime.sel("setActivationPolicy:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, activationPolicy) as BOOL
    }
    
    fun reportException(exception: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reportException:")
        ObjCRuntime.msgSend(null, ptr, sel, exception)
    }
    
    fun replyToApplicationShouldTerminate(shouldTerminate: BOOL): Unit {
        val sel = ObjCRuntime.sel("replyToApplicationShouldTerminate:")
        ObjCRuntime.msgSend(null, ptr, sel, shouldTerminate)
    }
    
    fun replyToOpenOrPrint(reply: NSApplicationDelegateReply): Unit {
        val sel = ObjCRuntime.sel("replyToOpenOrPrint:")
        ObjCRuntime.msgSend(null, ptr, sel, reply)
    }
    
    fun orderFrontCharacterPalette(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontCharacterPalette:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property sharedApplication
    fun sharedApplication(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedApplication")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSApplicationDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mainWindow
    fun mainWindow(): MemorySegment {
        val sel = ObjCRuntime.sel("mainWindow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property keyWindow
    fun keyWindow(): MemorySegment {
        val sel = ObjCRuntime.sel("keyWindow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property active
    fun isActive(): BOOL {
        val sel = ObjCRuntime.sel("isActive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property running
    fun isRunning(): BOOL {
        val sel = ObjCRuntime.sel("isRunning")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property applicationShouldSuppressHighDynamicRangeContent
    fun applicationShouldSuppressHighDynamicRangeContent(): BOOL {
        val sel = ObjCRuntime.sel("applicationShouldSuppressHighDynamicRangeContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property modalWindow
    fun modalWindow(): MemorySegment {
        val sel = ObjCRuntime.sel("modalWindow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windows
    /** @return NSArray<NSWindow *> * */
    fun windows(): MemorySegment {
        val sel = ObjCRuntime.sel("windows")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property mainMenu
    fun mainMenu(): MemorySegment {
        val sel = ObjCRuntime.sel("mainMenu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMainMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMainMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property helpMenu
    fun helpMenu(): MemorySegment {
        val sel = ObjCRuntime.sel("helpMenu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHelpMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHelpMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property applicationIconImage
    fun applicationIconImage(): MemorySegment {
        val sel = ObjCRuntime.sel("applicationIconImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setApplicationIconImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setApplicationIconImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dockTile
    fun dockTile(): MemorySegment {
        val sel = ObjCRuntime.sel("dockTile")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property presentationOptions
    fun presentationOptions(): NSApplicationPresentationOptions {
        val sel = ObjCRuntime.sel("presentationOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSApplicationPresentationOptions
    }
    fun setPresentationOptions(value: NSApplicationPresentationOptions) {
        val sel = ObjCRuntime.sel("setPresentationOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentSystemPresentationOptions
    fun currentSystemPresentationOptions(): NSApplicationPresentationOptions {
        val sel = ObjCRuntime.sel("currentSystemPresentationOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSApplicationPresentationOptions
    }
    
    // @property occlusionState
    fun occlusionState(): NSApplicationOcclusionState {
        val sel = ObjCRuntime.sel("occlusionState")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSApplicationOcclusionState
    }
    
    // @property protectedDataAvailable
    fun isProtectedDataAvailable(): BOOL {
        val sel = ObjCRuntime.sel("isProtectedDataAvailable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSAppearanceCustomization on NSApplication ─────────────────────────────────────────

fun NSApplication.appearance(): MemorySegment {
    val sel = ObjCRuntime.sel("appearance")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSApplication.setAppearance(appearance: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAppearance:")
    ObjCRuntime.msgSend(null, ptr, sel, appearance)
}

fun NSApplication.effectiveAppearance(): MemorySegment {
    val sel = ObjCRuntime.sel("effectiveAppearance")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property appearance
fun NSApplication.appearance(): MemorySegment {
    val sel = ObjCRuntime.sel("appearance")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSApplication.setAppearance(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAppearance:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property effectiveAppearance
fun NSApplication.effectiveAppearance(): MemorySegment {
    val sel = ObjCRuntime.sel("effectiveAppearance")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSEvent on NSApplication ─────────────────────────────────────────

fun NSApplication.sendEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sendEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSApplication.postEvent_atStart(event: MemorySegment, atStart: BOOL): Unit {
    val sel = ObjCRuntime.sel("postEvent:atStart:")
    ObjCRuntime.msgSend(null, ptr, sel, event, atStart)
}

fun NSApplication.nextEventMatchingMask_untilDate_inMode_dequeue(mask: NSEventMask, expiration: MemorySegment, mode: NSRunLoopMode, deqFlag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("nextEventMatchingMask:untilDate:inMode:dequeue:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask, expiration, mode, deqFlag) as MemorySegment
}

fun NSApplication.discardEventsMatchingMask_beforeEvent(mask: NSEventMask, lastEvent: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("discardEventsMatchingMask:beforeEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, mask, lastEvent)
}

fun NSApplication.currentEvent(): MemorySegment {
    val sel = ObjCRuntime.sel("currentEvent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property currentEvent
fun NSApplication.currentEvent(): MemorySegment {
    val sel = ObjCRuntime.sel("currentEvent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSResponder on NSApplication ─────────────────────────────────────────

fun NSApplication.sendAction_to_from(action: MemorySegment, target: MemorySegment, sender: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("sendAction:to:from:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, action, target, sender) as BOOL
}

fun NSApplication.targetForAction(action: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("targetForAction:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, action) as MemorySegment
}

fun NSApplication.targetForAction_to_from(action: MemorySegment, target: MemorySegment, sender: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("targetForAction:to:from:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, action, target, sender) as MemorySegment
}

fun NSApplication.tryToPerform_with(action: MemorySegment, `object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("tryToPerform:with:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, action, `object`) as BOOL
}

fun NSApplication.validRequestorForSendType_returnType(sendType: NSPasteboardType, returnType: NSPasteboardType): MemorySegment {
    val sel = ObjCRuntime.sel("validRequestorForSendType:returnType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendType, returnType) as MemorySegment
}

// ── Category: NSWindowsMenu on NSApplication ─────────────────────────────────────────

fun NSApplication.arrangeInFront(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("arrangeInFront:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSApplication.removeWindowsItem(win: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeWindowsItem:")
    ObjCRuntime.msgSend(null, ptr, sel, win)
}

fun NSApplication.addWindowsItem_title_filename(win: MemorySegment, string: MemorySegment, isFilename: BOOL): Unit {
    val sel = ObjCRuntime.sel("addWindowsItem:title:filename:")
    ObjCRuntime.msgSend(null, ptr, sel, win, string, isFilename)
}

fun NSApplication.changeWindowsItem_title_filename(win: MemorySegment, string: MemorySegment, isFilename: BOOL): Unit {
    val sel = ObjCRuntime.sel("changeWindowsItem:title:filename:")
    ObjCRuntime.msgSend(null, ptr, sel, win, string, isFilename)
}

fun NSApplication.updateWindowsItem(win: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("updateWindowsItem:")
    ObjCRuntime.msgSend(null, ptr, sel, win)
}

fun NSApplication.miniaturizeAll(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("miniaturizeAll:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSApplication.windowsMenu(): MemorySegment {
    val sel = ObjCRuntime.sel("windowsMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSApplication.setWindowsMenu(windowsMenu: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setWindowsMenu:")
    ObjCRuntime.msgSend(null, ptr, sel, windowsMenu)
}

// @property windowsMenu
fun NSApplication.windowsMenu(): MemorySegment {
    val sel = ObjCRuntime.sel("windowsMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSApplication.setWindowsMenu(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setWindowsMenu:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSFullKeyboardAccess on NSApplication ─────────────────────────────────────────

fun NSApplication.isFullKeyboardAccessEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isFullKeyboardAccessEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property fullKeyboardAccessEnabled
fun NSApplication.isFullKeyboardAccessEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isFullKeyboardAccessEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSServicesMenu on NSApplication ─────────────────────────────────────────

fun NSApplication.registerServicesMenuSendTypes_returnTypes(sendTypes: MemorySegment, returnTypes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerServicesMenuSendTypes:returnTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, sendTypes, returnTypes)
}

fun NSApplication.servicesMenu(): MemorySegment {
    val sel = ObjCRuntime.sel("servicesMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSApplication.setServicesMenu(servicesMenu: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setServicesMenu:")
    ObjCRuntime.msgSend(null, ptr, sel, servicesMenu)
}

// @property servicesMenu
fun NSApplication.servicesMenu(): MemorySegment {
    val sel = ObjCRuntime.sel("servicesMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSApplication.setServicesMenu(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setServicesMenu:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSServicesHandling on NSApplication ─────────────────────────────────────────

fun NSApplication.servicesProvider(): MemorySegment {
    val sel = ObjCRuntime.sel("servicesProvider")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSApplication.setServicesProvider(servicesProvider: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setServicesProvider:")
    ObjCRuntime.msgSend(null, ptr, sel, servicesProvider)
}

// @property servicesProvider
fun NSApplication.servicesProvider(): MemorySegment {
    val sel = ObjCRuntime.sel("servicesProvider")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSApplication.setServicesProvider(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setServicesProvider:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSStandardAboutPanel on NSApplication ─────────────────────────────────────────

fun NSApplication.orderFrontStandardAboutPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontStandardAboutPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSApplication.orderFrontStandardAboutPanelWithOptions(optionsDictionary: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontStandardAboutPanelWithOptions:")
    ObjCRuntime.msgSend(null, ptr, sel, optionsDictionary)
}

// ── Category: NSApplicationLayoutDirection on NSApplication ─────────────────────────────────────────

fun NSApplication.userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
    val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
}

// @property userInterfaceLayoutDirection
fun NSApplication.userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
    val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
}

// ── Category: NSRestorableUserInterface on NSApplication ─────────────────────────────────────────

fun NSApplication.disableRelaunchOnLogin(): Unit {
    val sel = ObjCRuntime.sel("disableRelaunchOnLogin")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSApplication.enableRelaunchOnLogin(): Unit {
    val sel = ObjCRuntime.sel("enableRelaunchOnLogin")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSRemoteNotifications on NSApplication ─────────────────────────────────────────

fun NSApplication.registerForRemoteNotifications(): Unit {
    val sel = ObjCRuntime.sel("registerForRemoteNotifications")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSApplication.unregisterForRemoteNotifications(): Unit {
    val sel = ObjCRuntime.sel("unregisterForRemoteNotifications")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSApplication.registerForRemoteNotificationTypes(types: NSRemoteNotificationType): Unit {
    val sel = ObjCRuntime.sel("registerForRemoteNotificationTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, types)
}

fun NSApplication.isRegisteredForRemoteNotifications(): BOOL {
    val sel = ObjCRuntime.sel("isRegisteredForRemoteNotifications")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSApplication.enabledRemoteNotificationTypes(): NSRemoteNotificationType {
    val sel = ObjCRuntime.sel("enabledRemoteNotificationTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRemoteNotificationType
}

// @property registeredForRemoteNotifications
fun NSApplication.isRegisteredForRemoteNotifications(): BOOL {
    val sel = ObjCRuntime.sel("isRegisteredForRemoteNotifications")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property enabledRemoteNotificationTypes
fun NSApplication.enabledRemoteNotificationTypes(): NSRemoteNotificationType {
    val sel = ObjCRuntime.sel("enabledRemoteNotificationTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRemoteNotificationType
}

// ── Category: NSDeprecated on NSApplication ─────────────────────────────────────────

fun NSApplication.runModalForWindow_relativeToWindow(window: MemorySegment, docWindow: MemorySegment): NSInteger {
    val sel = ObjCRuntime.sel("runModalForWindow:relativeToWindow:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, window, docWindow) as NSInteger
}

fun NSApplication.beginModalSessionForWindow_relativeToWindow(window: MemorySegment, docWindow: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("beginModalSessionForWindow:relativeToWindow:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, window, docWindow) as MemorySegment
}

fun NSApplication.application_printFiles(sender: MemorySegment, filenames: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("application:printFiles:")
    ObjCRuntime.msgSend(null, ptr, sel, sender, filenames)
}

fun NSApplication.beginSheet_modalForWindow_modalDelegate_didEndSelector_contextInfo(sheet: MemorySegment, docWindow: MemorySegment, modalDelegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("beginSheet:modalForWindow:modalDelegate:didEndSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, sheet, docWindow, modalDelegate, didEndSelector, contextInfo)
}

fun NSApplication.endSheet(sheet: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("endSheet:")
    ObjCRuntime.msgSend(null, ptr, sel, sheet)
}

fun NSApplication.endSheet_returnCode(sheet: MemorySegment, returnCode: NSInteger): Unit {
    val sel = ObjCRuntime.sel("endSheet:returnCode:")
    ObjCRuntime.msgSend(null, ptr, sel, sheet, returnCode)
}

fun NSApplication.makeWindowsPerform_inOrder(selector: MemorySegment, inOrder: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("makeWindowsPerform:inOrder:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, selector, inOrder) as MemorySegment
}

fun NSApplication.context(): MemorySegment {
    val sel = ObjCRuntime.sel("context")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property context
fun NSApplication.context(): MemorySegment {
    val sel = ObjCRuntime.sel("context")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSApplicationHelpExtension on NSApplication ─────────────────────────────────────────

fun NSApplication.activateContextHelpMode(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("activateContextHelpMode:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSApplication.showHelp(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("showHelp:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSTouchBarCustomization on NSApplication ─────────────────────────────────────────

fun NSApplication.toggleTouchBarCustomizationPalette(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleTouchBarCustomizationPalette:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSApplication.isAutomaticCustomizeTouchBarMenuItemEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticCustomizeTouchBarMenuItemEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSApplication.setAutomaticCustomizeTouchBarMenuItemEnabled(automaticCustomizeTouchBarMenuItemEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticCustomizeTouchBarMenuItemEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticCustomizeTouchBarMenuItemEnabled)
}

// @property automaticCustomizeTouchBarMenuItemEnabled
fun NSApplication.isAutomaticCustomizeTouchBarMenuItemEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticCustomizeTouchBarMenuItemEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSApplication.setAutomaticCustomizeTouchBarMenuItemEnabled(value: BOOL) {
    val sel = ObjCRuntime.sel("setAutomaticCustomizeTouchBarMenuItemEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSColorPanel on NSApplication ─────────────────────────────────────────

fun NSApplication.orderFrontColorPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontColorPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSPageLayoutPanel on NSApplication ─────────────────────────────────────────

fun NSApplication.runPageLayout(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("runPageLayout:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSScripting on NSApplication ─────────────────────────────────────────

/** @return NSArray<NSDocument *> * */
fun NSApplication.orderedDocuments(): MemorySegment {
    val sel = ObjCRuntime.sel("orderedDocuments")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSWindow *> * */
fun NSApplication.orderedWindows(): MemorySegment {
    val sel = ObjCRuntime.sel("orderedWindows")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property orderedDocuments
/** @return NSArray<NSDocument *> * */
fun NSApplication.orderedDocuments(): MemorySegment {
    val sel = ObjCRuntime.sel("orderedDocuments")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property orderedWindows
/** @return NSArray<NSWindow *> * */
fun NSApplication.orderedWindows(): MemorySegment {
    val sel = ObjCRuntime.sel("orderedWindows")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSUserInterfaceItemSearching on NSApplication ─────────────────────────────────────────

fun NSApplication.registerUserInterfaceItemSearchHandler(handler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerUserInterfaceItemSearchHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, handler)
}

fun NSApplication.unregisterUserInterfaceItemSearchHandler(handler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("unregisterUserInterfaceItemSearchHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, handler)
}

fun NSApplication.searchString_inUserInterfaceItemString_searchRange_foundRange(searchString: MemorySegment, stringToSearch: MemorySegment, searchRange: NSRange, foundRange: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("searchString:inUserInterfaceItemString:searchRange:foundRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, searchString, stringToSearch, searchRange, foundRange) as BOOL
}

// ── Category: NSWindowRestoration on NSApplication ─────────────────────────────────────────

fun NSApplication.restoreWindowWithIdentifier_state_completionHandler(identifier: NSUserInterfaceItemIdentifier, state: MemorySegment, completionHandler: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("restoreWindowWithIdentifier:state:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, identifier, state, completionHandler) as BOOL
}

// ── Category: NSRestorableStateExtension on NSApplication ─────────────────────────────────────────

fun NSApplication.extendStateRestoration(): Unit {
    val sel = ObjCRuntime.sel("extendStateRestoration")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSApplication.completeStateRestoration(): Unit {
    val sel = ObjCRuntime.sel("completeStateRestoration")
    ObjCRuntime.msgSend(null, ptr, sel)
}

