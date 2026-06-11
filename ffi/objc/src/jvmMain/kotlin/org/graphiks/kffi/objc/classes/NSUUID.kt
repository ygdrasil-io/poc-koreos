/**
 * Kotlin/JVM wrapper for Objective-C class: NSUUID
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSUUID(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUUID") }
        
        fun UUID(): MemorySegment {
            val sel = ObjCRuntime.sel("UUID")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithUUIDString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUUIDString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithUUIDString(string: String): MemorySegment = initWithUUIDString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun initWithUUIDBytes(bytes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUUIDBytes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes) as MemorySegment
    }
    
    fun getUUIDBytes(uuid: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getUUIDBytes:")
        ObjCRuntime.msgSend(null, ptr, sel, uuid)
    }
    
    fun compare(otherUUID: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherUUID) as NSComparisonResult
    }
    
    // @property UUIDString
    fun UUIDString(): MemorySegment {
        val sel = ObjCRuntime.sel("UUIDString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun UUIDStringAsString(): String = ObjCRuntime.toJavaString(UUIDString())
    
}

