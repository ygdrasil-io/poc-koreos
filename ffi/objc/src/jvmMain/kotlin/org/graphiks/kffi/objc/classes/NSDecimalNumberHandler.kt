/**
 * Kotlin/JVM wrapper for Objective-C class: NSDecimalNumberHandler
 * Superclass: NSObject
 * Protocols: NSDecimalNumberBehaviors, NSCoding
 */
open class NSDecimalNumberHandler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDecimalNumberHandler") }
        
        fun decimalNumberHandlerWithRoundingMode_scale_raiseOnExactness_raiseOnOverflow_raiseOnUnderflow_raiseOnDivideByZero(roundingMode: NSRoundingMode, scale: Short, exact: BOOL, overflow: BOOL, underflow: BOOL, divideByZero: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("decimalNumberHandlerWithRoundingMode:scale:raiseOnExactness:raiseOnOverflow:raiseOnUnderflow:raiseOnDivideByZero:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, roundingMode, scale, exact, overflow, underflow, divideByZero) as MemorySegment
        }
        
        fun defaultDecimalNumberHandler(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultDecimalNumberHandler")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithRoundingMode_scale_raiseOnExactness_raiseOnOverflow_raiseOnUnderflow_raiseOnDivideByZero(roundingMode: NSRoundingMode, scale: Short, exact: BOOL, overflow: BOOL, underflow: BOOL, divideByZero: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRoundingMode:scale:raiseOnExactness:raiseOnOverflow:raiseOnUnderflow:raiseOnDivideByZero:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, roundingMode, scale, exact, overflow, underflow, divideByZero) as MemorySegment
    }
    
    // @property defaultDecimalNumberHandler
    fun defaultDecimalNumberHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultDecimalNumberHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _scale: Int
    // ivar: _roundingMode: Any
    // ivar: _raiseOnExactness: Any
    // ivar: _raiseOnOverflow: Any
    // ivar: _raiseOnUnderflow: Any
    // ivar: _raiseOnDivideByZero: Any
    // ivar: _unused: Any
    // ivar: _reserved2: MemorySegment
    // ivar: _reserved: MemorySegment
}

