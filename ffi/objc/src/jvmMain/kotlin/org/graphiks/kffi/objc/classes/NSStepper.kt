package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSStepper
 * Superclass: NSControl
 * Protocols: NSAccessibilityStepper
 */
open class NSStepper(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStepper") }
        
    }
    
    // @property minValue
    fun minValue(): Double {
        val sel = ObjCRuntime.sel("minValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMinValue(value: Double) {
        val sel = ObjCRuntime.sel("setMinValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxValue
    fun maxValue(): Double {
        val sel = ObjCRuntime.sel("maxValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setMaxValue(value: Double) {
        val sel = ObjCRuntime.sel("setMaxValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property increment
    fun increment(): Double {
        val sel = ObjCRuntime.sel("increment")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setIncrement(value: Double) {
        val sel = ObjCRuntime.sel("setIncrement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property valueWraps
    fun valueWraps(): BOOL {
        val sel = ObjCRuntime.sel("valueWraps")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setValueWraps(value: BOOL) {
        val sel = ObjCRuntime.sel("setValueWraps:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autorepeat
    fun autorepeat(): BOOL {
        val sel = ObjCRuntime.sel("autorepeat")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutorepeat(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutorepeat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

