/**
 * Kotlin/JVM wrapper for Objective-C class: NSCache
 * Superclass: NSObject
 */
open class NSCache(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCache") }
        
    }
    
    fun objectForKey(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    fun setObject_forKey(obj: MemorySegment, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setObject:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, obj, key)
    }
    
    fun setObject_forKey_cost(obj: MemorySegment, key: MemorySegment, g: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("setObject:forKey:cost:")
        ObjCRuntime.msgSend(null, ptr, sel, obj, key, g)
    }
    
    fun removeObjectForKey(key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjectForKey:")
        ObjCRuntime.msgSend(null, ptr, sel, key)
    }
    
    fun removeAllObjects(): Unit {
        val sel = ObjCRuntime.sel("removeAllObjects")
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
    
    // @property delegate
    /** @return id<NSCacheDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property totalCostLimit
    fun totalCostLimit(): NSUInteger {
        val sel = ObjCRuntime.sel("totalCostLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setTotalCostLimit(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setTotalCostLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property countLimit
    fun countLimit(): NSUInteger {
        val sel = ObjCRuntime.sel("countLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setCountLimit(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setCountLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property evictsObjectsWithDiscardedContent
    fun evictsObjectsWithDiscardedContent(): BOOL {
        val sel = ObjCRuntime.sel("evictsObjectsWithDiscardedContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEvictsObjectsWithDiscardedContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setEvictsObjectsWithDiscardedContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

