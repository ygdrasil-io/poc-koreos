package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptCoercionHandler
 * Superclass: NSObject
 */
open class NSScriptCoercionHandler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptCoercionHandler") }
        
        open fun sharedCoercionHandler(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedCoercionHandler")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun coerceValue_toClass(value: MemorySegment, toClass: Class<*>): MemorySegment {
        val sel = ObjCRuntime.sel("coerceValue:toClass:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, toClass) as MemorySegment
    }
    
    open fun registerCoercer_selector_toConvertFromClass_toClass(coercer: MemorySegment, selector: MemorySegment, fromClass: Class<*>, toClass: Class<*>): Unit {
        val sel = ObjCRuntime.sel("registerCoercer:selector:toConvertFromClass:toClass:")
        ObjCRuntime.msgSend(null, ptr, sel, coercer, selector, fromClass, toClass)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _coercers: MemorySegment
}

