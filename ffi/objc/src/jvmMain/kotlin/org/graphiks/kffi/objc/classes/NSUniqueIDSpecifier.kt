/**
 * Kotlin/JVM wrapper for Objective-C class: NSUniqueIDSpecifier
 * Superclass: NSScriptObjectSpecifier
 */
open class NSUniqueIDSpecifier(ptr: MemorySegment) : NSScriptObjectSpecifier(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUniqueIDSpecifier") }
        
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun initWithContainerClassDescription_containerSpecifier_key_uniqueID(classDesc: MemorySegment, container: MemorySegment, property: MemorySegment, uniqueID: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContainerClassDescription:containerSpecifier:key:uniqueID:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, classDesc, container, property, uniqueID) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithContainerClassDescription_containerSpecifier_key_uniqueID(classDesc: MemorySegment, container: MemorySegment, property: String, uniqueID: MemorySegment): MemorySegment = initWithContainerClassDescription_containerSpecifier_key_uniqueID(classDesc, container, ObjCRuntime.newNSString(Arena.global(), property), uniqueID)
    
    // @property uniqueID
    fun uniqueID(): MemorySegment {
        val sel = ObjCRuntime.sel("uniqueID")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUniqueID(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUniqueID:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _uniqueID: MemorySegment
}

