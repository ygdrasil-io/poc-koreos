/**
 * Kotlin/JVM wrapper for Objective-C class: NSNotificationQueue
 * Superclass: NSObject
 */
open class NSNotificationQueue(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNotificationQueue") }
        
        fun defaultQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithNotificationCenter(notificationCenter: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithNotificationCenter:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, notificationCenter) as MemorySegment
    }
    
    fun enqueueNotification_postingStyle(notification: MemorySegment, postingStyle: NSPostingStyle): Unit {
        val sel = ObjCRuntime.sel("enqueueNotification:postingStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, postingStyle)
    }
    
    fun enqueueNotification_postingStyle_coalesceMask_forModes(notification: MemorySegment, postingStyle: NSPostingStyle, coalesceMask: NSNotificationCoalescing, modes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enqueueNotification:postingStyle:coalesceMask:forModes:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, postingStyle, coalesceMask, modes)
    }
    
    fun dequeueNotificationsMatching_coalesceMask(notification: MemorySegment, coalesceMask: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("dequeueNotificationsMatching:coalesceMask:")
        ObjCRuntime.msgSend(null, ptr, sel, notification, coalesceMask)
    }
    
    // @property defaultQueue
    fun defaultQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

