package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSConditionLock
 * Superclass: NSObject
 * Protocols: NSLocking
 */
open class NSConditionLock(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSConditionLock") }
        
    }
    
    open fun initWithCondition(condition: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCondition:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, condition) as MemorySegment
    }
    
    open fun lockWhenCondition(condition: NSInteger): Unit {
        val sel = ObjCRuntime.sel("lockWhenCondition:")
        ObjCRuntime.msgSend(null, ptr, sel, condition)
    }
    
    open fun tryLock(): BOOL {
        val sel = ObjCRuntime.sel("tryLock")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun tryLockWhenCondition(condition: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("tryLockWhenCondition:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, condition) as BOOL
    }
    
    open fun unlockWithCondition(condition: NSInteger): Unit {
        val sel = ObjCRuntime.sel("unlockWithCondition:")
        ObjCRuntime.msgSend(null, ptr, sel, condition)
    }
    
    open fun lockBeforeDate(limit: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("lockBeforeDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, limit) as BOOL
    }
    
    open fun lockWhenCondition_beforeDate(condition: NSInteger, limit: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("lockWhenCondition:beforeDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, condition, limit) as BOOL
    }
    
    // @property condition
    open fun condition(): NSInteger {
        val sel = ObjCRuntime.sel("condition")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
}

