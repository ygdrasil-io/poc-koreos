package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSEvent
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSEvent(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSEvent") }
        
        open fun eventWithEventRef(eventRef: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("eventWithEventRef:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, eventRef) as MemorySegment
        }
        
        open fun eventWithCGEvent(cgEvent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("eventWithCGEvent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cgEvent) as MemorySegment
        }
        
        open fun startPeriodicEventsAfterDelay_withPeriod(delay: NSTimeInterval, period: NSTimeInterval): Unit {
            val sel = ObjCRuntime.sel("startPeriodicEventsAfterDelay:withPeriod:")
            ObjCRuntime.msgSend(null, _class, sel, delay, period)
        }
        
        open fun stopPeriodicEvents(): Unit {
            val sel = ObjCRuntime.sel("stopPeriodicEvents")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun mouseEventWithType_location_modifierFlags_timestamp_windowNumber_context_eventNumber_clickCount_pressure(type: NSEventType, location: NSPoint, flags: NSEventModifierFlags, time: NSTimeInterval, wNum: NSInteger, unusedPassNil: MemorySegment, eNum: NSInteger, cNum: NSInteger, pressure: Float): MemorySegment {
            val sel = ObjCRuntime.sel("mouseEventWithType:location:modifierFlags:timestamp:windowNumber:context:eventNumber:clickCount:pressure:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), flags, time, wNum, unusedPassNil, eNum, cNum, pressure) as MemorySegment
        }
        
        open fun keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(type: NSEventType, location: NSPoint, flags: NSEventModifierFlags, time: NSTimeInterval, wNum: NSInteger, unusedPassNil: MemorySegment, keys: MemorySegment, ukeys: MemorySegment, flag: BOOL, code: Any): MemorySegment {
            val sel = ObjCRuntime.sel("keyEventWithType:location:modifierFlags:timestamp:windowNumber:context:characters:charactersIgnoringModifiers:isARepeat:keyCode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), flags, time, wNum, unusedPassNil, keys, ukeys, flag, code) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(type: NSEventType, location: NSPoint, flags: NSEventModifierFlags, time: NSTimeInterval, wNum: NSInteger, unusedPassNil: MemorySegment, keys: String, ukeys: String, flag: BOOL, code: Any): MemorySegment = keyEventWithType_location_modifierFlags_timestamp_windowNumber_context_characters_charactersIgnoringModifiers_isARepeat_keyCode(type, location, flags, time, wNum, unusedPassNil, ObjCRuntime.newNSString(Arena.global(), keys), ObjCRuntime.newNSString(Arena.global(), ukeys), flag, code)
        
        open fun enterExitEventWithType_location_modifierFlags_timestamp_windowNumber_context_eventNumber_trackingNumber_userData(type: NSEventType, location: NSPoint, flags: NSEventModifierFlags, time: NSTimeInterval, wNum: NSInteger, unusedPassNil: MemorySegment, eNum: NSInteger, tNum: NSInteger, `data`: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("enterExitEventWithType:location:modifierFlags:timestamp:windowNumber:context:eventNumber:trackingNumber:userData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), flags, time, wNum, unusedPassNil, eNum, tNum, `data`) as MemorySegment
        }
        
        open fun otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2(type: NSEventType, location: NSPoint, flags: NSEventModifierFlags, time: NSTimeInterval, wNum: NSInteger, unusedPassNil: MemorySegment, subtype: Short, d1: NSInteger, d2: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), flags, time, wNum, unusedPassNil, subtype, d1, d2) as MemorySegment
        }
        
        open fun addGlobalMonitorForEventsMatchingMask_handler(mask: NSEventMask, block: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("addGlobalMonitorForEventsMatchingMask:handler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, mask, block) as MemorySegment
        }
        
        open fun addLocalMonitorForEventsMatchingMask_handler(mask: NSEventMask, block: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("addLocalMonitorForEventsMatchingMask:handler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, mask, block) as MemorySegment
        }
        
        open fun removeMonitor(eventMonitor: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("removeMonitor:")
            ObjCRuntime.msgSend(null, _class, sel, eventMonitor)
        }
        
        open fun isMouseCoalescingEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isMouseCoalescingEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun setMouseCoalescingEnabled(mouseCoalescingEnabled: BOOL): Unit {
            val sel = ObjCRuntime.sel("setMouseCoalescingEnabled:")
            ObjCRuntime.msgSend(null, _class, sel, mouseCoalescingEnabled)
        }
        
        open fun isSwipeTrackingFromScrollEventsEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isSwipeTrackingFromScrollEventsEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun mouseLocation(): NSPoint {
            val sel = ObjCRuntime.sel("mouseLocation")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), _class, sel) as NSPoint
        }
        
        open fun modifierFlags(): NSEventModifierFlags {
            val sel = ObjCRuntime.sel("modifierFlags")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSEventModifierFlags
        }
        
        open fun pressedMouseButtons(): NSUInteger {
            val sel = ObjCRuntime.sel("pressedMouseButtons")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, _class, sel) as NSUInteger
        }
        
        open fun doubleClickInterval(): NSTimeInterval {
            val sel = ObjCRuntime.sel("doubleClickInterval")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as NSTimeInterval
        }
        
        open fun keyRepeatDelay(): NSTimeInterval {
            val sel = ObjCRuntime.sel("keyRepeatDelay")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as NSTimeInterval
        }
        
        open fun keyRepeatInterval(): NSTimeInterval {
            val sel = ObjCRuntime.sel("keyRepeatInterval")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as NSTimeInterval
        }
        
    }
    
    open fun charactersByApplyingModifiers(modifiers: NSEventModifierFlags): MemorySegment {
        val sel = ObjCRuntime.sel("charactersByApplyingModifiers:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, modifiers) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun charactersByApplyingModifiersAsString(modifiers: NSEventModifierFlags): String = ObjCRuntime.toJavaString(charactersByApplyingModifiers(modifiers))
    
    /** @return NSSet<NSTouch *> * */
    open fun touchesMatchingPhase_inView(phase: NSTouchPhase, view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("touchesMatchingPhase:inView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, phase, view) as MemorySegment
    }
    
    /** @return NSSet<NSTouch *> * */
    open fun allTouches(): MemorySegment {
        val sel = ObjCRuntime.sel("allTouches")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSSet<NSTouch *> * */
    open fun touchesForView(view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("touchesForView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view) as MemorySegment
    }
    
    /** @return NSArray<NSTouch *> * */
    open fun coalescedTouchesForTouch(touch: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("coalescedTouchesForTouch:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, touch) as MemorySegment
    }
    
    open fun trackSwipeEventWithOptions_dampenAmountThresholdMin_max_usingHandler(options: NSEventSwipeTrackingOptions, minDampenThreshold: CGFloat, maxDampenThreshold: CGFloat, trackingHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("trackSwipeEventWithOptions:dampenAmountThresholdMin:max:usingHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, options, minDampenThreshold, maxDampenThreshold, trackingHandler)
    }
    
    // @property type
    open fun type(): NSEventType {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventType
    }
    
    // @property modifierFlags
    }
    
    // @property timestamp
    open fun timestamp(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timestamp")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property window
    open fun window(): MemorySegment {
        val sel = ObjCRuntime.sel("window")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windowNumber
    open fun windowNumber(): NSInteger {
        val sel = ObjCRuntime.sel("windowNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property context
    open fun context(): MemorySegment {
        val sel = ObjCRuntime.sel("context")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property clickCount
    open fun clickCount(): NSInteger {
        val sel = ObjCRuntime.sel("clickCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property buttonNumber
    open fun buttonNumber(): NSInteger {
        val sel = ObjCRuntime.sel("buttonNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property eventNumber
    open fun eventNumber(): NSInteger {
        val sel = ObjCRuntime.sel("eventNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property pressure
    open fun pressure(): Float {
        val sel = ObjCRuntime.sel("pressure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property locationInWindow
    open fun locationInWindow(): NSPoint {
        val sel = ObjCRuntime.sel("locationInWindow")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property deltaX
    open fun deltaX(): CGFloat {
        val sel = ObjCRuntime.sel("deltaX")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property deltaY
    open fun deltaY(): CGFloat {
        val sel = ObjCRuntime.sel("deltaY")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property deltaZ
    open fun deltaZ(): CGFloat {
        val sel = ObjCRuntime.sel("deltaZ")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property hasPreciseScrollingDeltas
    open fun hasPreciseScrollingDeltas(): BOOL {
        val sel = ObjCRuntime.sel("hasPreciseScrollingDeltas")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property scrollingDeltaX
    open fun scrollingDeltaX(): CGFloat {
        val sel = ObjCRuntime.sel("scrollingDeltaX")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property scrollingDeltaY
    open fun scrollingDeltaY(): CGFloat {
        val sel = ObjCRuntime.sel("scrollingDeltaY")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property momentumPhase
    open fun momentumPhase(): NSEventPhase {
        val sel = ObjCRuntime.sel("momentumPhase")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventPhase
    }
    
    // @property directionInvertedFromDevice
    open fun isDirectionInvertedFromDevice(): BOOL {
        val sel = ObjCRuntime.sel("isDirectionInvertedFromDevice")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property characters
    open fun characters(): MemorySegment {
        val sel = ObjCRuntime.sel("characters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun charactersAsString(): String = ObjCRuntime.toJavaString(characters())
    
    // @property charactersIgnoringModifiers
    open fun charactersIgnoringModifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("charactersIgnoringModifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun charactersIgnoringModifiersAsString(): String = ObjCRuntime.toJavaString(charactersIgnoringModifiers())
    
    // @property ARepeat
    open fun isARepeat(): BOOL {
        val sel = ObjCRuntime.sel("isARepeat")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property keyCode
    open fun keyCode(): Any {
        val sel = ObjCRuntime.sel("keyCode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as Any
    }
    
    // @property trackingNumber
    open fun trackingNumber(): NSInteger {
        val sel = ObjCRuntime.sel("trackingNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property userData
    open fun userData(): MemorySegment {
        val sel = ObjCRuntime.sel("userData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property trackingArea
    open fun trackingArea(): MemorySegment {
        val sel = ObjCRuntime.sel("trackingArea")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property subtype
    open fun subtype(): NSEventSubtype {
        val sel = ObjCRuntime.sel("subtype")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventSubtype
    }
    
    // @property data1
    open fun data1(): NSInteger {
        val sel = ObjCRuntime.sel("data1")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property data2
    open fun data2(): NSInteger {
        val sel = ObjCRuntime.sel("data2")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property eventRef
    open fun eventRef(): MemorySegment {
        val sel = ObjCRuntime.sel("eventRef")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property CGEvent
    open fun CGEvent(): MemorySegment {
        val sel = ObjCRuntime.sel("CGEvent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property mouseCoalescingEnabled
    }
    }
    
    // @property magnification
    open fun magnification(): CGFloat {
        val sel = ObjCRuntime.sel("magnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property deviceID
    open fun deviceID(): NSUInteger {
        val sel = ObjCRuntime.sel("deviceID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property rotation
    open fun rotation(): Float {
        val sel = ObjCRuntime.sel("rotation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property absoluteX
    open fun absoluteX(): NSInteger {
        val sel = ObjCRuntime.sel("absoluteX")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property absoluteY
    open fun absoluteY(): NSInteger {
        val sel = ObjCRuntime.sel("absoluteY")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property absoluteZ
    open fun absoluteZ(): NSInteger {
        val sel = ObjCRuntime.sel("absoluteZ")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property buttonMask
    open fun buttonMask(): NSEventButtonMask {
        val sel = ObjCRuntime.sel("buttonMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventButtonMask
    }
    
    // @property tilt
    open fun tilt(): NSPoint {
        val sel = ObjCRuntime.sel("tilt")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property tangentialPressure
    open fun tangentialPressure(): Float {
        val sel = ObjCRuntime.sel("tangentialPressure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property vendorDefined
    open fun vendorDefined(): MemorySegment {
        val sel = ObjCRuntime.sel("vendorDefined")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property vendorID
    open fun vendorID(): NSUInteger {
        val sel = ObjCRuntime.sel("vendorID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property tabletID
    open fun tabletID(): NSUInteger {
        val sel = ObjCRuntime.sel("tabletID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property pointingDeviceID
    open fun pointingDeviceID(): NSUInteger {
        val sel = ObjCRuntime.sel("pointingDeviceID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property systemTabletID
    open fun systemTabletID(): NSUInteger {
        val sel = ObjCRuntime.sel("systemTabletID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property vendorPointingDeviceType
    open fun vendorPointingDeviceType(): NSUInteger {
        val sel = ObjCRuntime.sel("vendorPointingDeviceType")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property pointingDeviceSerialNumber
    open fun pointingDeviceSerialNumber(): NSUInteger {
        val sel = ObjCRuntime.sel("pointingDeviceSerialNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property uniqueID
    open fun uniqueID(): Any {
        val sel = ObjCRuntime.sel("uniqueID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Any
    }
    
    // @property capabilityMask
    open fun capabilityMask(): NSUInteger {
        val sel = ObjCRuntime.sel("capabilityMask")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property pointingDeviceType
    open fun pointingDeviceType(): NSPointingDeviceType {
        val sel = ObjCRuntime.sel("pointingDeviceType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPointingDeviceType
    }
    
    // @property enteringProximity
    open fun isEnteringProximity(): BOOL {
        val sel = ObjCRuntime.sel("isEnteringProximity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property phase
    open fun phase(): NSEventPhase {
        val sel = ObjCRuntime.sel("phase")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventPhase
    }
    
    // @property stage
    open fun stage(): NSInteger {
        val sel = ObjCRuntime.sel("stage")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property stageTransition
    open fun stageTransition(): CGFloat {
        val sel = ObjCRuntime.sel("stageTransition")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property associatedEventsMask
    open fun associatedEventsMask(): NSEventMask {
        val sel = ObjCRuntime.sel("associatedEventsMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventMask
    }
    
    // @property pressureBehavior
    open fun pressureBehavior(): NSPressureBehavior {
        val sel = ObjCRuntime.sel("pressureBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPressureBehavior
    }
    
    // @property swipeTrackingFromScrollEventsEnabled
    }
    
    // @property mouseLocation
    }
    
    // @property pressedMouseButtons
    }
    
    // @property doubleClickInterval
    }
    
    // @property keyRepeatDelay
    }
    
    // @property keyRepeatInterval
    }
    
}

