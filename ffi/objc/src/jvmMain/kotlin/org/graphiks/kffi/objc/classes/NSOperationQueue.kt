/**
 * Kotlin/JVM wrapper for Objective-C class: NSOperationQueue
 * Superclass: NSObject
 * Protocols: NSProgressReporting
 */
open class NSOperationQueue(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOperationQueue") }
        
        fun currentQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("currentQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun mainQueue(): MemorySegment {
            val sel = ObjCRuntime.sel("mainQueue")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun addOperation(op: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addOperation:")
        ObjCRuntime.msgSend(null, ptr, sel, op)
    }
    
    fun addOperations_waitUntilFinished(ops: MemorySegment, wait: BOOL): Unit {
        val sel = ObjCRuntime.sel("addOperations:waitUntilFinished:")
        ObjCRuntime.msgSend(null, ptr, sel, ops, wait)
    }
    
    fun addOperationWithBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addOperationWithBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun addBarrierBlock(barrier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addBarrierBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, barrier)
    }
    
    fun cancelAllOperations(): Unit {
        val sel = ObjCRuntime.sel("cancelAllOperations")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun waitUntilAllOperationsAreFinished(): Unit {
        val sel = ObjCRuntime.sel("waitUntilAllOperationsAreFinished")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property progress
    fun progress(): MemorySegment {
        val sel = ObjCRuntime.sel("progress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property maxConcurrentOperationCount
    fun maxConcurrentOperationCount(): NSInteger {
        val sel = ObjCRuntime.sel("maxConcurrentOperationCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaxConcurrentOperationCount(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaxConcurrentOperationCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property suspended
    fun isSuspended(): BOOL {
        val sel = ObjCRuntime.sel("isSuspended")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSuspended(value: BOOL) {
        val sel = ObjCRuntime.sel("setSuspended:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property qualityOfService
    fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property underlyingQueue
    fun underlyingQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("underlyingQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUnderlyingQueue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUnderlyingQueue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentQueue
    fun currentQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("currentQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property mainQueue
    fun mainQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("mainQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
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
fun NSOperationQueue.operations(): MemorySegment {
    val sel = ObjCRuntime.sel("operations")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property operationCount
fun NSOperationQueue.operationCount(): NSUInteger {
    val sel = ObjCRuntime.sel("operationCount")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

