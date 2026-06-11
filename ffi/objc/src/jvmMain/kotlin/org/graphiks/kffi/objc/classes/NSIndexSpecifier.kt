/**
 * Kotlin/JVM wrapper for Objective-C class: NSIndexSpecifier
 * Superclass: NSScriptObjectSpecifier
 */
open class NSIndexSpecifier(ptr: MemorySegment) : NSScriptObjectSpecifier(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSIndexSpecifier") }
        
    }
    
    fun initWithContainerClassDescription_containerSpecifier_key_index(classDesc: MemorySegment, container: MemorySegment, property: MemorySegment, index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContainerClassDescription:containerSpecifier:key:index:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, classDesc, container, property, index) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithContainerClassDescription_containerSpecifier_key_index(classDesc: MemorySegment, container: MemorySegment, property: String, index: NSInteger): MemorySegment = initWithContainerClassDescription_containerSpecifier_key_index(classDesc, container, ObjCRuntime.newNSString(Arena.global(), property), index)
    
    // @property index
    fun index(): NSInteger {
        val sel = ObjCRuntime.sel("index")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _index: NSInteger
}

