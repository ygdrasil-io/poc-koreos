package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDecimalNumberHandler
 * Superclass: NSObject
 * Protocols: NSDecimalNumberBehaviors, NSCoding
 */
open class NSDecimalNumberHandler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDecimalNumberHandler") }
        
        open fun decimalNumberHandlerWithRoundingMode_scale_raiseOnExactness_raiseOnOverflow_raiseOnUnderflow_raiseOnDivideByZero(roundingMode: NSRoundingMode, scale: Short, exact: BOOL, overflow: BOOL, underflow: BOOL, divideByZero: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("decimalNumberHandlerWithRoundingMode:scale:raiseOnExactness:raiseOnOverflow:raiseOnUnderflow:raiseOnDivideByZero:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, roundingMode, scale, exact, overflow, underflow, divideByZero) as MemorySegment
        }
        
        open fun defaultDecimalNumberHandler(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultDecimalNumberHandler")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithRoundingMode_scale_raiseOnExactness_raiseOnOverflow_raiseOnUnderflow_raiseOnDivideByZero(roundingMode: NSRoundingMode, scale: Short, exact: BOOL, overflow: BOOL, underflow: BOOL, divideByZero: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRoundingMode:scale:raiseOnExactness:raiseOnOverflow:raiseOnUnderflow:raiseOnDivideByZero:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, roundingMode, scale, exact, overflow, underflow, divideByZero) as MemorySegment
    }
    
    // @property defaultDecimalNumberHandler
}

