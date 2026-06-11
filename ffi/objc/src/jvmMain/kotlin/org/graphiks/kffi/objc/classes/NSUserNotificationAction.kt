/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserNotificationAction
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSUserNotificationAction(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserNotificationAction") }
        
        fun actionWithIdentifier_title(identifier: MemorySegment, title: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("actionWithIdentifier:title:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, title) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun actionWithIdentifier_title(identifier: String, title: String): MemorySegment = actionWithIdentifier_title(ObjCRuntime.newNSString(Arena.global(), identifier), ObjCRuntime.newNSString(Arena.global(), title))
        
    }
    
    // @property identifier
    fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun identifierAsString(): String = ObjCRuntime.toJavaString(identifier())
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
}

