package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSliderAccessoryBehavior
 * Superclass: NSObject
 * Protocols: NSCoding, NSCopying
 */
open class NSSliderAccessoryBehavior(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSliderAccessoryBehavior") }
        
        open fun behaviorWithTarget_action(target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("behaviorWithTarget:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, target, action) as MemorySegment
        }
        
        open fun behaviorWithHandler(handler: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("behaviorWithHandler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, handler) as MemorySegment
        }
        
        open fun automaticBehavior(): MemorySegment {
            val sel = ObjCRuntime.sel("automaticBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun valueStepBehavior(): MemorySegment {
            val sel = ObjCRuntime.sel("valueStepBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun valueResetBehavior(): MemorySegment {
            val sel = ObjCRuntime.sel("valueResetBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun handleAction(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("handleAction:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property automaticBehavior
}

