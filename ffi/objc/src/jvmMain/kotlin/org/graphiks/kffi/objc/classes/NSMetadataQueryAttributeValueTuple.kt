/**
 * Kotlin/JVM wrapper for Objective-C class: NSMetadataQueryAttributeValueTuple
 * Superclass: NSObject
 */
open class NSMetadataQueryAttributeValueTuple(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMetadataQueryAttributeValueTuple") }
        
    }
    
    // @property attribute
    fun attribute(): MemorySegment {
        val sel = ObjCRuntime.sel("attribute")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun attributeAsString(): String = ObjCRuntime.toJavaString(attribute())
    
    // @property value
    fun value(): MemorySegment {
        val sel = ObjCRuntime.sel("value")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property count
    fun count(): NSUInteger {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _attr: MemorySegment
    // ivar: _value: MemorySegment
    // ivar: _count: NSUInteger
    // ivar: _reserved: MemorySegment
}

