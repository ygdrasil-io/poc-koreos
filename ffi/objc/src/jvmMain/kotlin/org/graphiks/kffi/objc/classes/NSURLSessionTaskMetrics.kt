/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionTaskMetrics
 * Superclass: NSObject
 */
open class NSURLSessionTaskMetrics(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionTaskMetrics") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property transactionMetrics
    /** @return NSArray<NSURLSessionTaskTransactionMetrics *> * */
    fun transactionMetrics(): MemorySegment {
        val sel = ObjCRuntime.sel("transactionMetrics")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property taskInterval
    fun taskInterval(): MemorySegment {
        val sel = ObjCRuntime.sel("taskInterval")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property redirectCount
    fun redirectCount(): NSUInteger {
        val sel = ObjCRuntime.sel("redirectCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

