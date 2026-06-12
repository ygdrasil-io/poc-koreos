package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSBackgroundActivityScheduler
 * Superclass: NSObject
 */
open class NSBackgroundActivityScheduler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBackgroundActivityScheduler") }
        
    }
    
    open fun initWithIdentifier(identifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithIdentifier(identifier: String): MemorySegment = initWithIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
    
    open fun scheduleWithBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("scheduleWithBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    open fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property identifier
    open fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun identifierAsString(): String = ObjCRuntime.toJavaString(identifier())
    
    // @property qualityOfService
    open fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    open fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property repeats
    open fun repeats(): BOOL {
        val sel = ObjCRuntime.sel("repeats")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setRepeats(value: BOOL) {
        val sel = ObjCRuntime.sel("setRepeats:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interval
    open fun interval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("interval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    open fun setInterval(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tolerance
    open fun tolerance(): NSTimeInterval {
        val sel = ObjCRuntime.sel("tolerance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    open fun setTolerance(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTolerance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldDefer
    open fun shouldDefer(): BOOL {
        val sel = ObjCRuntime.sel("shouldDefer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

