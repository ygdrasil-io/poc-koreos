package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGestureRecognizer
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSGestureRecognizer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGestureRecognizer") }
        
    }
    
    open fun initWithTarget_action(target: MemorySegment, action: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTarget:action:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, action) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun locationInView(view: MemorySegment): NSPoint {
        val sel = ObjCRuntime.sel("locationInView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, view) as NSPoint
    }
    
    // @property target
    open fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    open fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    open fun state(): NSGestureRecognizerState {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGestureRecognizerState
    }
    
    // @property delegate
    /** @return id<NSGestureRecognizerDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enabled
    open fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property view
    open fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property pressureConfiguration
    open fun pressureConfiguration(): MemorySegment {
        val sel = ObjCRuntime.sel("pressureConfiguration")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setPressureConfiguration(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPressureConfiguration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysPrimaryMouseButtonEvents
    open fun delaysPrimaryMouseButtonEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysPrimaryMouseButtonEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysPrimaryMouseButtonEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysPrimaryMouseButtonEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysSecondaryMouseButtonEvents
    open fun delaysSecondaryMouseButtonEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysSecondaryMouseButtonEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysSecondaryMouseButtonEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysSecondaryMouseButtonEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysOtherMouseButtonEvents
    open fun delaysOtherMouseButtonEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysOtherMouseButtonEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysOtherMouseButtonEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysOtherMouseButtonEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysKeyEvents
    open fun delaysKeyEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysKeyEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysKeyEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysKeyEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysMagnificationEvents
    open fun delaysMagnificationEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysMagnificationEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysMagnificationEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysMagnificationEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delaysRotationEvents
    open fun delaysRotationEvents(): BOOL {
        val sel = ObjCRuntime.sel("delaysRotationEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDelaysRotationEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setDelaysRotationEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property modifierFlags
    open fun modifierFlags(): NSEventModifierFlags {
        val sel = ObjCRuntime.sel("modifierFlags")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventModifierFlags
    }
    
}

// ── Category: NSTouchBar on NSGestureRecognizer ─────────────────────────────────────────

fun NSGestureRecognizer.allowedTouchTypes(): NSTouchTypeMask {
    val sel = ObjCRuntime.sel("allowedTouchTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchTypeMask
}

fun NSGestureRecognizer.setAllowedTouchTypes(allowedTouchTypes: NSTouchTypeMask): Unit {
    val sel = ObjCRuntime.sel("setAllowedTouchTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, allowedTouchTypes)
}

// @property allowedTouchTypes
fun NSGestureRecognizer.allowedTouchTypes(): NSTouchTypeMask {
    val sel = ObjCRuntime.sel("allowedTouchTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchTypeMask
}
fun NSGestureRecognizer.setAllowedTouchTypes(value: NSTouchTypeMask) {
    val sel = ObjCRuntime.sel("setAllowedTouchTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSSubclassUse on NSGestureRecognizer ─────────────────────────────────────────

fun NSGestureRecognizer.reset(): Unit {
    val sel = ObjCRuntime.sel("reset")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSGestureRecognizer.canPreventGestureRecognizer(preventedGestureRecognizer: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("canPreventGestureRecognizer:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, preventedGestureRecognizer) as BOOL
}

fun NSGestureRecognizer.canBePreventedByGestureRecognizer(preventingGestureRecognizer: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("canBePreventedByGestureRecognizer:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, preventingGestureRecognizer) as BOOL
}

fun NSGestureRecognizer.shouldRequireFailureOfGestureRecognizer(otherGestureRecognizer: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("shouldRequireFailureOfGestureRecognizer:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherGestureRecognizer) as BOOL
}

fun NSGestureRecognizer.shouldBeRequiredToFailByGestureRecognizer(otherGestureRecognizer: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("shouldBeRequiredToFailByGestureRecognizer:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherGestureRecognizer) as BOOL
}

fun NSGestureRecognizer.mouseDown(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("mouseDown:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.rightMouseDown(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rightMouseDown:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.otherMouseDown(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("otherMouseDown:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.mouseUp(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("mouseUp:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.rightMouseUp(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rightMouseUp:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.otherMouseUp(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("otherMouseUp:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.mouseDragged(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("mouseDragged:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.rightMouseDragged(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rightMouseDragged:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.otherMouseDragged(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("otherMouseDragged:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.mouseCancelled(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("mouseCancelled:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.keyDown(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("keyDown:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.keyUp(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("keyUp:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.flagsChanged(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("flagsChanged:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.tabletPoint(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("tabletPoint:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.magnifyWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("magnifyWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.rotateWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rotateWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.pressureChangeWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("pressureChangeWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.touchesBeganWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("touchesBeganWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.touchesMovedWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("touchesMovedWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.touchesEndedWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("touchesEndedWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.touchesCancelledWithEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("touchesCancelledWithEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSGestureRecognizer.state(): NSGestureRecognizerState {
    val sel = ObjCRuntime.sel("state")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGestureRecognizerState
}

fun NSGestureRecognizer.setState(state: NSGestureRecognizerState): Unit {
    val sel = ObjCRuntime.sel("setState:")
    ObjCRuntime.msgSend(null, ptr, sel, state)
}

// @property state
fun NSGestureRecognizer.state(): NSGestureRecognizerState {
    val sel = ObjCRuntime.sel("state")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGestureRecognizerState
}
fun NSGestureRecognizer.setState(value: NSGestureRecognizerState) {
    val sel = ObjCRuntime.sel("setState:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

