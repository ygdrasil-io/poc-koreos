package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOperationQueue
 * Superclass: NSObject
 * Protocols: NSProgressReporting
 */
open class NSOperationQueue(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOperationQueue") }
        
        open fun currentQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("currentQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun mainQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("mainQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun addOperation(op: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addOperation:")
        ObjCRuntime.msgSend(null, ptr, sel, op)
    }
    
    open fun addOperations_waitUntilFinished(ops: MemorySegment, wait: BOOL): Unit {
        val sel = ObjCRuntime.sel("addOperations:waitUntilFinished:")
        ObjCRuntime.msgSend(null, ptr, sel, ops, wait)
    }
    
    open fun addOperationWithBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addOperationWithBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    open fun addBarrierBlock(barrier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addBarrierBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, barrier)
    }
    
    open fun cancelAllOperations(): Unit {
        val sel = ObjCRuntime.sel("cancelAllOperations")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun waitUntilAllOperationsAreFinished(): Unit {
        val sel = ObjCRuntime.sel("waitUntilAllOperationsAreFinished")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property progress
    open fun progress(): MemorySegment {
        val sel = ObjCRuntime.sel("progress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property maxConcurrentOperationCount
    open fun maxConcurrentOperationCount(): NSInteger {
        val sel = ObjCRuntime.sel("maxConcurrentOperationCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setMaxConcurrentOperationCount(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaxConcurrentOperationCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property suspended
    open fun isSuspended(): BOOL {
        val sel = ObjCRuntime.sel("isSuspended")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSuspended(value: BOOL) {
        val sel = ObjCRuntime.sel("setSuspended:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property qualityOfService
    open fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    open fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property underlyingQueue
    open fun underlyingQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("underlyingQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setUnderlyingQueue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUnderlyingQueue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentQueue
}

// ── Category: NSDeprecated on NSOperationQueue ─────────────────────────────────────────

/** @return NSArray<__kindof NSOperation *> * */
fun NSOperationQueue.operations(): MemorySegment {
    val sel = ObjCRuntime.sel("operations")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSOperationQueue.operationCount(): NSUInteger {
    val sel = ObjCRuntime.sel("operationCount")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// @property operations
/** @return NSArray<__kindof NSOperation *> * */