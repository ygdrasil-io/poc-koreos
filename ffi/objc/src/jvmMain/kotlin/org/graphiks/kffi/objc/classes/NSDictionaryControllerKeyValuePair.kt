/**
 * Kotlin/JVM wrapper for Objective-C class: NSDictionaryControllerKeyValuePair
 * Superclass: NSObject
 */
open class NSDictionaryControllerKeyValuePair(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDictionaryControllerKeyValuePair") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property key
    fun key(): MemorySegment {
        val sel = ObjCRuntime.sel("key")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setKey(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun keyAsString(): String = ObjCRuntime.toJavaString(key())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setKey(value: String) = setKey(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property value
    fun value(): MemorySegment {
        val sel = ObjCRuntime.sel("value")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property localizedKey
    fun localizedKey(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedKey")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocalizedKey(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocalizedKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedKeyAsString(): String = ObjCRuntime.toJavaString(localizedKey())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLocalizedKey(value: String) = setLocalizedKey(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property explicitlyIncluded
    fun isExplicitlyIncluded(): BOOL {
        val sel = ObjCRuntime.sel("isExplicitlyIncluded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

