/**
 * Kotlin/JVM wrapper for Objective-C class: NSWhoseSpecifier
 * Superclass: NSScriptObjectSpecifier
 */
open class NSWhoseSpecifier(ptr: MemorySegment) : NSScriptObjectSpecifier(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWhoseSpecifier") }
        
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun initWithContainerClassDescription_containerSpecifier_key_test(classDesc: MemorySegment, container: MemorySegment, property: MemorySegment, test: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContainerClassDescription:containerSpecifier:key:test:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, classDesc, container, property, test) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithContainerClassDescription_containerSpecifier_key_test(classDesc: MemorySegment, container: MemorySegment, property: String, test: MemorySegment): MemorySegment = initWithContainerClassDescription_containerSpecifier_key_test(classDesc, container, ObjCRuntime.newNSString(Arena.global(), property), test)
    
    // @property test
    fun test(): MemorySegment {
        val sel = ObjCRuntime.sel("test")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTest(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTest:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property startSubelementIdentifier
    fun startSubelementIdentifier(): NSWhoseSubelementIdentifier {
        val sel = ObjCRuntime.sel("startSubelementIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWhoseSubelementIdentifier
    }
    fun setStartSubelementIdentifier(value: NSWhoseSubelementIdentifier) {
        val sel = ObjCRuntime.sel("setStartSubelementIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property startSubelementIndex
    fun startSubelementIndex(): NSInteger {
        val sel = ObjCRuntime.sel("startSubelementIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setStartSubelementIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setStartSubelementIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property endSubelementIdentifier
    fun endSubelementIdentifier(): NSWhoseSubelementIdentifier {
        val sel = ObjCRuntime.sel("endSubelementIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWhoseSubelementIdentifier
    }
    fun setEndSubelementIdentifier(value: NSWhoseSubelementIdentifier) {
        val sel = ObjCRuntime.sel("setEndSubelementIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property endSubelementIndex
    fun endSubelementIndex(): NSInteger {
        val sel = ObjCRuntime.sel("endSubelementIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setEndSubelementIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setEndSubelementIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _test: MemorySegment
    // ivar: _startSubelementIdentifier: NSWhoseSubelementIdentifier
    // ivar: _startSubelementIndex: NSInteger
    // ivar: _endSubelementIdentifier: NSWhoseSubelementIdentifier
    // ivar: _endSubelementIndex: NSInteger
}

