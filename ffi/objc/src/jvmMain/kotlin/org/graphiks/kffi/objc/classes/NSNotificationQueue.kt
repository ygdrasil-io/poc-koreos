package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSNotificationQueue
 * Superclass: NSObject
 */
open class NSNotificationQueue(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNotificationQueue") }
        
        open fun defaultQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithNotificationCenter(notificationCenter: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithNotificationCenter:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, notificationCenter) as MemorySegment
    }
    
    open fun enqueueNotification_postingStyle(notification: MemorySegment, postingStyle: NSPostingStyle): Unit {
        val sel = ObjCRuntime.sel("enqueueNotification:postingStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, postingStyle)
    }
    
    open fun enqueueNotification_postingStyle_coalesceMask_forModes(notification: MemorySegment, postingStyle: NSPostingStyle, coalesceMask: NSNotificationCoalescing, modes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enqueueNotification:postingStyle:coalesceMask:forModes:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, postingStyle, coalesceMask, modes)
    }
    
    open fun dequeueNotificationsMatching_coalesceMask(notification: MemorySegment, coalesceMask: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("dequeueNotificationsMatching:coalesceMask:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, coalesceMask)
    }
    
    // @property defaultQueue
    }
    
}

