/**
 * Kotlin/JVM wrapper for Objective-C class: NSOperation
 * Superclass: NSObject
 */
open class NSOperation(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOperation") }
        
    }
    
    fun start(): Unit {
        val sel = ObjCRuntime.sel("start")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun main(): Unit {
        val sel = ObjCRuntime.sel("main")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addDependency(op: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addDependency:")
        ObjCRuntime.msgSend(null, ptr, sel, op)
    }
    
    fun removeDependency(op: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeDependency:")
        ObjCRuntime.msgSend(null, ptr, sel, op)
    }
    
    fun waitUntilFinished(): Unit {
        val sel = ObjCRuntime.sel("waitUntilFinished")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property cancelled
    fun isCancelled(): BOOL {
        val sel = ObjCRuntime.sel("isCancelled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property executing
    fun isExecuting(): BOOL {
        val sel = ObjCRuntime.sel("isExecuting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property finished
    fun isFinished(): BOOL {
        val sel = ObjCRuntime.sel("isFinished")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property concurrent
    fun isConcurrent(): BOOL {
        val sel = ObjCRuntime.sel("isConcurrent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property asynchronous
    fun isAsynchronous(): BOOL {
        val sel = ObjCRuntime.sel("isAsynchronous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property ready
    fun isReady(): BOOL {
        val sel = ObjCRuntime.sel("isReady")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property dependencies
    /** @return NSArray<NSOperation *> * */
    fun dependencies(): MemorySegment {
        val sel = ObjCRuntime.sel("dependencies")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property queuePriority
    fun queuePriority(): NSOperationQueuePriority {
        val sel = ObjCRuntime.sel("queuePriority")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSOperationQueuePriority
    }
    fun setQueuePriority(value: NSOperationQueuePriority) {
        val sel = ObjCRuntime.sel("setQueuePriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property completionBlock
    fun completionBlock(): MemorySegment {
        val sel = ObjCRuntime.sel("completionBlock")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCompletionBlock(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCompletionBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property threadPriority
    fun threadPriority(): Double {
        val sel = ObjCRuntime.sel("threadPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setThreadPriority(value: Double) {
        val sel = ObjCRuntime.sel("setThreadPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property qualityOfService
    fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
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
    
}

