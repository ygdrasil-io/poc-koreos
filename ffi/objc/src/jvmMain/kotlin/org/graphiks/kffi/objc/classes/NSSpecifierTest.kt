/**
 * Kotlin/JVM wrapper for Objective-C class: NSSpecifierTest
 * Superclass: NSScriptWhoseTest
 */
open class NSSpecifierTest(ptr: MemorySegment) : NSScriptWhoseTest(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSpecifierTest") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    fun initWithObjectSpecifier_comparisonOperator_testObject(obj1: MemorySegment, compOp: NSTestComparisonOperation, obj2: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObjectSpecifier:comparisonOperator:testObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, obj1, compOp, obj2) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _comparisonOperator: NSTestComparisonOperation
    // ivar: _object1: MemorySegment
    // ivar: _object2: MemorySegment
}

