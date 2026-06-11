/**
 * Kotlin/JVM wrapper for Objective-C class: NSMetadataItem
 * Superclass: NSObject
 */
open class NSMetadataItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMetadataItem") }
        
    }
    
    fun initWithURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun valueForAttribute(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("valueForAttribute:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun valueForAttribute(key: String): MemorySegment = valueForAttribute(ObjCRuntime.newNSString(Arena.global(), key))
    
    /** @return NSDictionary<NSString *,id> * */
    fun valuesForAttributes(keys: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("valuesForAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys) as MemorySegment
    }
    
    // @property attributes
    /** @return NSArray<NSString *> * */
    fun attributes(): MemorySegment {
        val sel = ObjCRuntime.sel("attributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _item: MemorySegment
    // ivar: _reserved: MemorySegment
}

