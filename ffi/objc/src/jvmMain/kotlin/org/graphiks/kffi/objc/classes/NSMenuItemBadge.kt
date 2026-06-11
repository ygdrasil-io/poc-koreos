/**
 * Kotlin/JVM wrapper for Objective-C class: NSMenuItemBadge
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSMenuItemBadge(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMenuItemBadge") }
        
        fun updatesWithCount(itemCount: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("updatesWithCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, itemCount) as MemorySegment
        }
        
        fun newItemsWithCount(itemCount: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("newItemsWithCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, itemCount) as MemorySegment
        }
        
        fun alertsWithCount(itemCount: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("alertsWithCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, itemCount) as MemorySegment
        }
        
    }
    
    fun initWithCount_type(itemCount: NSInteger, type: NSMenuItemBadgeType): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCount:type:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemCount, type) as MemorySegment
    }
    
    fun initWithCount(itemCount: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCount:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemCount) as MemorySegment
    }
    
    fun initWithString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString(string: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property itemCount
    fun itemCount(): NSInteger {
        val sel = ObjCRuntime.sel("itemCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property type
    fun type(): NSMenuItemBadgeType {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuItemBadgeType
    }
    
    // @property stringValue
    fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
}

