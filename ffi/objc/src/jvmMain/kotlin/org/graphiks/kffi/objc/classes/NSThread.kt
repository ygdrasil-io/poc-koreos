package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSThread
 * Superclass: NSObject
 */
open class NSThread(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSThread") }
        
        open fun detachNewThreadWithBlock(block: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("detachNewThreadWithBlock:")
            ObjCRuntime.msgSend(null, _class, sel, block)
        }
        
        open fun detachNewThreadSelector_toTarget_withObject(selector: MemorySegment, target: MemorySegment, argument: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("detachNewThreadSelector:toTarget:withObject:")
            ObjCRuntime.msgSend(null, _class, sel, selector, target, argument)
        }
        
        open fun isMultiThreaded(): BOOL {
            val sel = ObjCRuntime.sel("isMultiThreaded")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun sleepUntilDate(date: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("sleepUntilDate:")
            ObjCRuntime.msgSend(null, _class, sel, date)
        }
        
        open fun sleepForTimeInterval(ti: NSTimeInterval): Unit {
            val sel = ObjCRuntime.sel("sleepForTimeInterval:")
            ObjCRuntime.msgSend(null, _class, sel, ti)
        }
        
        open fun exit(): Unit {
            val sel = ObjCRuntime.sel("exit")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun threadPriority(): Double {
            val sel = ObjCRuntime.sel("threadPriority")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as Double
        }
        
        open fun setThreadPriority(p: Double): BOOL {
            val sel = ObjCRuntime.sel("setThreadPriority:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, p) as BOOL
        }
        
        open fun currentThread(): MemorySegment {
            val sel = ObjCRuntime.sel("currentThread")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSNumber *> * */
        open fun callStackReturnAddresses(): MemorySegment {
            val sel = ObjCRuntime.sel("callStackReturnAddresses")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        open fun callStackSymbols(): MemorySegment {
            val sel = ObjCRuntime.sel("callStackSymbols")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun isMainThread(): BOOL {
            val sel = ObjCRuntime.sel("isMainThread")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun mainThread(): MemorySegment {
            val sel = ObjCRuntime.sel("mainThread")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithTarget_selector_object(target: MemorySegment, selector: MemorySegment, argument: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTarget:selector:object:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, selector, argument) as MemorySegment
    }
    
    open fun initWithBlock(block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithBlock:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, block) as MemorySegment
    }
    
    open fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun start(): Unit {
        val sel = ObjCRuntime.sel("start")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun main(): Unit {
        val sel = ObjCRuntime.sel("main")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property currentThread
    open fun threadDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("threadDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property threadPriority
    open fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    open fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property callStackReturnAddresses
    /** @return NSArray<NSNumber *> * */
    /** @return NSArray<NSString *> * */
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
    
    // @property stackSize
    open fun stackSize(): NSUInteger {
        val sel = ObjCRuntime.sel("stackSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    open fun setStackSize(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setStackSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property isMainThread
    open fun isExecuting(): BOOL {
        val sel = ObjCRuntime.sel("isExecuting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property finished
    open fun isFinished(): BOOL {
        val sel = ObjCRuntime.sel("isFinished")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property cancelled
    open fun isCancelled(): BOOL {
        val sel = ObjCRuntime.sel("isCancelled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _private: MemorySegment
    // ivar: _bytes: MemorySegment
}

