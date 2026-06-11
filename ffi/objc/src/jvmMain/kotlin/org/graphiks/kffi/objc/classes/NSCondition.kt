/**
 * Kotlin/JVM wrapper for Objective-C class: NSCondition
 * Superclass: NSObject
 * Protocols: NSLocking
 */
open class NSCondition(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCondition") }
        
    }
    
    fun wait(): Unit {
        val sel = ObjCRuntime.sel("wait")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun waitUntilDate(limit: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("waitUntilDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, limit) as BOOL
    }
    
    fun signal(): Unit {
        val sel = ObjCRuntime.sel("signal")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun broadcast(): Unit {
        val sel = ObjCRuntime.sel("broadcast")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
}

