package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMethodSignature
 * Superclass: NSObject
 */
open class NSMethodSignature(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMethodSignature") }
        
        open fun signatureWithObjCTypes(types: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("signatureWithObjCTypes:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, types) as MemorySegment
        }
        
    }
    
    open fun getArgumentTypeAtIndex(idx: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("getArgumentTypeAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, idx) as MemorySegment
    }
    
    open fun isOneway(): BOOL {
        val sel = ObjCRuntime.sel("isOneway")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property numberOfArguments
    open fun numberOfArguments(): NSUInteger {
        val sel = ObjCRuntime.sel("numberOfArguments")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property frameLength
    open fun frameLength(): NSUInteger {
        val sel = ObjCRuntime.sel("frameLength")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property methodReturnType
    open fun methodReturnType(): MemorySegment {
        val sel = ObjCRuntime.sel("methodReturnType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property methodReturnLength
    open fun methodReturnLength(): NSUInteger {
        val sel = ObjCRuntime.sel("methodReturnLength")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

