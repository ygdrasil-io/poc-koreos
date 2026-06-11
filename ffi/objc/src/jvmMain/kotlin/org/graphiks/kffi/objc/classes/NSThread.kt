/**
 * Kotlin/JVM wrapper for Objective-C class: NSThread
 * Superclass: NSObject
 */
open class NSThread(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSThread") }
        
        fun detachNewThreadWithBlock(block: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("detachNewThreadWithBlock:")
            ObjCRuntime.msgSend(null, _class, sel, block)
        }
        
        fun detachNewThreadSelector_toTarget_withObject(selector: MemorySegment, target: MemorySegment, argument: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("detachNewThreadSelector:toTarget:withObject:")
            ObjCRuntime.msgSend(null, _class, sel, selector, target, argument)
        }
        
        fun isMultiThreaded(): BOOL {
            val sel = ObjCRuntime.sel("isMultiThreaded")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun sleepUntilDate(date: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("sleepUntilDate:")
            ObjCRuntime.msgSend(null, _class, sel, date)
        }
        
        fun sleepForTimeInterval(ti: NSTimeInterval): Unit {
            val sel = ObjCRuntime.sel("sleepForTimeInterval:")
            ObjCRuntime.msgSend(null, _class, sel, ti)
        }
        
        fun exit(): Unit {
            val sel = ObjCRuntime.sel("exit")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        fun threadPriority(): Double {
            val sel = ObjCRuntime.sel("threadPriority")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as Double
        }
        
        fun setThreadPriority(p: Double): BOOL {
            val sel = ObjCRuntime.sel("setThreadPriority:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, p) as BOOL
        }
        
        fun currentThread(): MemorySegment {
            val sel = ObjCRuntime.sel("currentThread")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSNumber *> * */
        fun callStackReturnAddresses(): MemorySegment {
            val sel = ObjCRuntime.sel("callStackReturnAddresses")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        fun callStackSymbols(): MemorySegment {
            val sel = ObjCRuntime.sel("callStackSymbols")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun isMainThread(): BOOL {
            val sel = ObjCRuntime.sel("isMainThread")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun mainThread(): MemorySegment {
            val sel = ObjCRuntime.sel("mainThread")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithTarget_selector_object(target: MemorySegment, selector: MemorySegment, argument: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTarget:selector:object:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, selector, argument) as MemorySegment
    }
    
    fun initWithBlock(block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithBlock:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, block) as MemorySegment
    }
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun start(): Unit {
        val sel = ObjCRuntime.sel("start")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun main(): Unit {
        val sel = ObjCRuntime.sel("main")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property currentThread
    fun currentThread(): MemorySegment {
        val sel = ObjCRuntime.sel("currentThread")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property threadDictionary
    fun threadDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("threadDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property callStackReturnAddresses
    /** @return NSArray<NSNumber *> * */
    fun callStackReturnAddresses(): MemorySegment {
        val sel = ObjCRuntime.sel("callStackReturnAddresses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property callStackSymbols
    /** @return NSArray<NSString *> * */
    fun callStackSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("callStackSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property stackSize
    fun stackSize(): NSUInteger {
        val sel = ObjCRuntime.sel("stackSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setStackSize(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setStackSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property isMainThread
    fun isMainThread(): BOOL {
        val sel = ObjCRuntime.sel("isMainThread")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property mainThread
    fun mainThread(): MemorySegment {
        val sel = ObjCRuntime.sel("mainThread")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property cancelled
    fun isCancelled(): BOOL {
        val sel = ObjCRuntime.sel("isCancelled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _private: MemorySegment
    // ivar: _bytes: MemorySegment
}

