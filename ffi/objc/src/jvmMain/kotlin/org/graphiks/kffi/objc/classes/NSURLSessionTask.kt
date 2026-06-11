/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionTask
 * Superclass: NSObject
 * Protocols: NSCopying, NSProgressReporting
 */
open class NSURLSessionTask(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionTask") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun suspend(): Unit {
        val sel = ObjCRuntime.sel("suspend")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resume(): Unit {
        val sel = ObjCRuntime.sel("resume")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property taskIdentifier
    fun taskIdentifier(): NSUInteger {
        val sel = ObjCRuntime.sel("taskIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property originalRequest
    fun originalRequest(): MemorySegment {
        val sel = ObjCRuntime.sel("originalRequest")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentRequest
    fun currentRequest(): MemorySegment {
        val sel = ObjCRuntime.sel("currentRequest")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property response
    fun response(): MemorySegment {
        val sel = ObjCRuntime.sel("response")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSURLSessionTaskDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property progress
    fun progress(): MemorySegment {
        val sel = ObjCRuntime.sel("progress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property earliestBeginDate
    fun earliestBeginDate(): MemorySegment {
        val sel = ObjCRuntime.sel("earliestBeginDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEarliestBeginDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEarliestBeginDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property countOfBytesClientExpectsToSend
    fun countOfBytesClientExpectsToSend(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesClientExpectsToSend")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    fun setCountOfBytesClientExpectsToSend(value: int64_t) {
        val sel = ObjCRuntime.sel("setCountOfBytesClientExpectsToSend:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property countOfBytesClientExpectsToReceive
    fun countOfBytesClientExpectsToReceive(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesClientExpectsToReceive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    fun setCountOfBytesClientExpectsToReceive(value: int64_t) {
        val sel = ObjCRuntime.sel("setCountOfBytesClientExpectsToReceive:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property countOfBytesSent
    fun countOfBytesSent(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesSent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfBytesReceived
    fun countOfBytesReceived(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesReceived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfBytesExpectedToSend
    fun countOfBytesExpectedToSend(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesExpectedToSend")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property countOfBytesExpectedToReceive
    fun countOfBytesExpectedToReceive(): int64_t {
        val sel = ObjCRuntime.sel("countOfBytesExpectedToReceive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    
    // @property taskDescription
    fun taskDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("taskDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTaskDescription(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTaskDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun taskDescriptionAsString(): String = ObjCRuntime.toJavaString(taskDescription())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTaskDescription(value: String) = setTaskDescription(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property state
    fun state(): NSURLSessionTaskState {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionTaskState
    }
    
    // @property error
    fun error(): MemorySegment {
        val sel = ObjCRuntime.sel("error")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property priority
    fun priority(): Float {
        val sel = ObjCRuntime.sel("priority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setPriority(value: Float) {
        val sel = ObjCRuntime.sel("setPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prefersIncrementalDelivery
    fun prefersIncrementalDelivery(): BOOL {
        val sel = ObjCRuntime.sel("prefersIncrementalDelivery")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPrefersIncrementalDelivery(value: BOOL) {
        val sel = ObjCRuntime.sel("setPrefersIncrementalDelivery:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

