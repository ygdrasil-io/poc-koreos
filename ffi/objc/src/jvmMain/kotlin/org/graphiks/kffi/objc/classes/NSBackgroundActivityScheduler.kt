/**
 * Kotlin/JVM wrapper for Objective-C class: NSBackgroundActivityScheduler
 * Superclass: NSObject
 */
open class NSBackgroundActivityScheduler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBackgroundActivityScheduler") }
        
    }
    
    fun initWithIdentifier(identifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithIdentifier(identifier: String): MemorySegment = initWithIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
    
    fun scheduleWithBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("scheduleWithBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property identifier
    fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun identifierAsString(): String = ObjCRuntime.toJavaString(identifier())
    
    // @property qualityOfService
    fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property repeats
    fun repeats(): BOOL {
        val sel = ObjCRuntime.sel("repeats")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRepeats(value: BOOL) {
        val sel = ObjCRuntime.sel("setRepeats:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interval
    fun interval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("interval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setInterval(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tolerance
    fun tolerance(): NSTimeInterval {
        val sel = ObjCRuntime.sel("tolerance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setTolerance(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setTolerance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldDefer
    fun shouldDefer(): BOOL {
        val sel = ObjCRuntime.sel("shouldDefer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

