/**
 * Kotlin/JVM wrapper for Objective-C class: NSNotification
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSNotification(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNotification") }
        
    }
    
    fun initWithName_object_userInfo(name: NSNotificationName, `object`: MemorySegment, userInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithName:object:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, `object`, userInfo) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property name
    fun name(): NSNotificationName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNotificationName
    }
    
    // @property object
    fun `object`(): MemorySegment {
        val sel = ObjCRuntime.sel("object")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property userInfo
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSNotificationCreation on NSNotification ─────────────────────────────────────────

fun NSNotification.init(): MemorySegment {
    val sel = ObjCRuntime.sel("init")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class method: +[NSNotification notificationWithName:object:]
fun NSNotification_notificationWithName_object(aName: NSNotificationName, anObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("notificationWithName:object:")
    val cls = ObjCRuntime.getClass("NSNotification")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, aName, anObject) as MemorySegment
}

// Class method: +[NSNotification notificationWithName:object:userInfo:]
fun NSNotification_notificationWithName_object_userInfo(aName: NSNotificationName, anObject: MemorySegment, aUserInfo: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("notificationWithName:object:userInfo:")
    val cls = ObjCRuntime.getClass("NSNotification")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, aName, anObject, aUserInfo) as MemorySegment
}

