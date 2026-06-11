/**
 * Kotlin/JVM interface for Objective-C protocol: NSUserNotificationCenterDelegate
 * Inherits protocols: NSObject
 */
interface NSUserNotificationCenterDelegate : NSObject {
    // @optional
    fun userNotificationCenter_didDeliverNotification(center: MemorySegment, notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'userNotificationCenter:didDeliverNotification:' not implemented")
    
    // @optional
    fun userNotificationCenter_didActivateNotification(center: MemorySegment, notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'userNotificationCenter:didActivateNotification:' not implemented")
    
    // @optional
    fun userNotificationCenter_shouldPresentNotification(center: MemorySegment, notification: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'userNotificationCenter:shouldPresentNotification:' not implemented")
    
}

