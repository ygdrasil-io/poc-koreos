/**
 * Kotlin/JVM wrapper for Objective-C class: NSResponder
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSResponder(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSResponder") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun tryToPerform_with(action: MemorySegment, `object`: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("tryToPerform:with:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, action, `object`) as BOOL
    }
    
    fun performKeyEquivalent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performKeyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun validRequestorForSendType_returnType(sendType: NSPasteboardType, returnType: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("validRequestorForSendType:returnType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendType, returnType) as MemorySegment
    }
    
    fun mouseDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun rightMouseDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rightMouseDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun otherMouseDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("otherMouseDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseUp(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseUp:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun rightMouseUp(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rightMouseUp:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun otherMouseUp(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("otherMouseUp:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseMoved(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseMoved:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseDragged(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseDragged:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseCancelled(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseCancelled:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun scrollWheel(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("scrollWheel:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun rightMouseDragged(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rightMouseDragged:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun otherMouseDragged(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("otherMouseDragged:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseEntered(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseEntered:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun mouseExited(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseExited:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun keyDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("keyDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun keyUp(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("keyUp:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun flagsChanged(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("flagsChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun tabletPoint(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("tabletPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun tabletProximity(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("tabletProximity:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun cursorUpdate(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cursorUpdate:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun magnifyWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("magnifyWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun rotateWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rotateWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun swipeWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("swipeWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun beginGestureWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginGestureWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun endGestureWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("endGestureWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun smartMagnifyWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("smartMagnifyWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun changeModeWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeModeWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun touchesBeganWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("touchesBeganWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun touchesMovedWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("touchesMovedWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun touchesEndedWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("touchesEndedWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun touchesCancelledWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("touchesCancelledWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun quickLookWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("quickLookWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun pressureChangeWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("pressureChangeWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun contextMenuKeyDown(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("contextMenuKeyDown:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun noResponderFor(eventSelector: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noResponderFor:")
        ObjCRuntime.msgSend(null, ptr, sel, eventSelector)
    }
    
    fun becomeFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("becomeFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun resignFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("resignFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun interpretKeyEvents(eventArray: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("interpretKeyEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, eventArray)
    }
    
    fun flushBufferedKeyEvents(): Unit {
        val sel = ObjCRuntime.sel("flushBufferedKeyEvents")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun showContextHelp(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showContextHelp:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun helpRequested(eventPtr: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("helpRequested:")
        ObjCRuntime.msgSend(null, ptr, sel, eventPtr)
    }
    
    fun shouldBeTreatedAsInkEvent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("shouldBeTreatedAsInkEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun wantsScrollEventsForSwipeTrackingOnAxis(axis: NSEventGestureAxis): BOOL {
        val sel = ObjCRuntime.sel("wantsScrollEventsForSwipeTrackingOnAxis:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, axis) as BOOL
    }
    
    fun wantsForwardedScrollEventsForAxis(axis: NSEventGestureAxis): BOOL {
        val sel = ObjCRuntime.sel("wantsForwardedScrollEventsForAxis:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, axis) as BOOL
    }
    
    fun supplementalTargetForAction_sender(action: MemorySegment, sender: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("supplementalTargetForAction:sender:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, action, sender) as MemorySegment
    }
    
    // @property nextResponder
    fun nextResponder(): MemorySegment {
        val sel = ObjCRuntime.sel("nextResponder")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNextResponder(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNextResponder:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property acceptsFirstResponder
    fun acceptsFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("acceptsFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSStandardKeyBindingMethods on NSResponder ─────────────────────────────────────────

// ── Category: NSUndoSupport on NSResponder ─────────────────────────────────────────

fun NSResponder.undoManager(): MemorySegment {
    val sel = ObjCRuntime.sel("undoManager")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property undoManager
fun NSResponder.undoManager(): MemorySegment {
    val sel = ObjCRuntime.sel("undoManager")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSControlEditingSupport on NSResponder ─────────────────────────────────────────

fun NSResponder.validateProposedFirstResponder_forEvent(responder: MemorySegment, event: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("validateProposedFirstResponder:forEvent:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, responder, event) as BOOL
}

// ── Category: NSErrorPresentation on NSResponder ─────────────────────────────────────────

fun NSResponder.presentError_modalForWindow_delegate_didPresentSelector_contextInfo(error: MemorySegment, window: MemorySegment, delegate: MemorySegment, didPresentSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("presentError:modalForWindow:delegate:didPresentSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, error, window, delegate, didPresentSelector, contextInfo)
}

fun NSResponder.presentError(error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("presentError:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
}

fun NSResponder.willPresentError(error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("willPresentError:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, error) as MemorySegment
}

// ── Category: NSTextFinderSupport on NSResponder ─────────────────────────────────────────

fun NSResponder.performTextFinderAction(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performTextFinderAction:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSWindowTabbing on NSResponder ─────────────────────────────────────────

fun NSResponder.newWindowForTab(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("newWindowForTab:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSWritingToolsSupport on NSResponder ─────────────────────────────────────────

fun NSResponder.showWritingTools(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("showWritingTools:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSDeprecated on NSResponder ─────────────────────────────────────────

fun NSResponder.performMnemonic(string: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("performMnemonic:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
}

// ── Category: NSUserActivity on NSResponder ─────────────────────────────────────────

fun NSResponder.updateUserActivityState(userActivity: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("updateUserActivityState:")
    ObjCRuntime.msgSend(null, ptr, sel, userActivity)
}

fun NSResponder.userActivity(): MemorySegment {
    val sel = ObjCRuntime.sel("userActivity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSResponder.setUserActivity(userActivity: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setUserActivity:")
    ObjCRuntime.msgSend(null, ptr, sel, userActivity)
}

// @property userActivity
fun NSResponder.userActivity(): MemorySegment {
    val sel = ObjCRuntime.sel("userActivity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSResponder.setUserActivity(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setUserActivity:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSTouchBarProvider on NSResponder ─────────────────────────────────────────

fun NSResponder.makeTouchBar(): MemorySegment {
    val sel = ObjCRuntime.sel("makeTouchBar")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSResponder.touchBar(): MemorySegment {
    val sel = ObjCRuntime.sel("touchBar")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSResponder.setTouchBar(touchBar: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTouchBar:")
    ObjCRuntime.msgSend(null, ptr, sel, touchBar)
}

// @property touchBar
fun NSResponder.touchBar(): MemorySegment {
    val sel = ObjCRuntime.sel("touchBar")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSResponder.setTouchBar(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setTouchBar:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSInterfaceStyle on NSResponder ─────────────────────────────────────────

fun NSResponder.interfaceStyle(): NSInterfaceStyle {
    val sel = ObjCRuntime.sel("interfaceStyle")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInterfaceStyle
}

fun NSResponder.setInterfaceStyle(interfaceStyle: NSInterfaceStyle): Unit {
    val sel = ObjCRuntime.sel("setInterfaceStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, interfaceStyle)
}

// ── Category: NSRestorableState on NSResponder ─────────────────────────────────────────

fun NSResponder.encodeRestorableStateWithCoder(coder: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("encodeRestorableStateWithCoder:")
    ObjCRuntime.msgSend(null, ptr, sel, coder)
}

fun NSResponder.encodeRestorableStateWithCoder_backgroundQueue(coder: MemorySegment, queue: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("encodeRestorableStateWithCoder:backgroundQueue:")
    ObjCRuntime.msgSend(null, ptr, sel, coder, queue)
}

fun NSResponder.restoreStateWithCoder(coder: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("restoreStateWithCoder:")
    ObjCRuntime.msgSend(null, ptr, sel, coder)
}

fun NSResponder.invalidateRestorableState(): Unit {
    val sel = ObjCRuntime.sel("invalidateRestorableState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// Class method: +[NSResponder allowedClassesForRestorableStateKeyPath:]
fun NSResponder_allowedClassesForRestorableStateKeyPath(keyPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("allowedClassesForRestorableStateKeyPath:")
    val cls = ObjCRuntime.getClass("NSResponder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, keyPath) as MemorySegment
}

// Class method: +[NSResponder restorableStateKeyPaths]
fun NSResponder_restorableStateKeyPaths(): MemorySegment {
    val sel = ObjCRuntime.sel("restorableStateKeyPaths")
    val cls = ObjCRuntime.getClass("NSResponder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property restorableStateKeyPaths
/** @return NSArray<NSString *> * */
fun NSResponder.restorableStateKeyPaths(): MemorySegment {
    val sel = ObjCRuntime.sel("restorableStateKeyPaths")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

