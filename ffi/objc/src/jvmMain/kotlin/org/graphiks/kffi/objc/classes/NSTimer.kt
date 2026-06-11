/**
 * Kotlin/JVM wrapper for Objective-C class: NSTimer
 * Superclass: NSObject
 */
open class NSTimer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTimer") }
        
        fun timerWithTimeInterval_invocation_repeats(ti: NSTimeInterval, invocation: MemorySegment, yesOrNo: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("timerWithTimeInterval:invocation:repeats:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ti, invocation, yesOrNo) as MemorySegment
        }
        
        fun scheduledTimerWithTimeInterval_invocation_repeats(ti: NSTimeInterval, invocation: MemorySegment, yesOrNo: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("scheduledTimerWithTimeInterval:invocation:repeats:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ti, invocation, yesOrNo) as MemorySegment
        }
        
        fun timerWithTimeInterval_target_selector_userInfo_repeats(ti: NSTimeInterval, aTarget: MemorySegment, aSelector: MemorySegment, userInfo: MemorySegment, yesOrNo: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("timerWithTimeInterval:target:selector:userInfo:repeats:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ti, aTarget, aSelector, userInfo, yesOrNo) as MemorySegment
        }
        
        fun scheduledTimerWithTimeInterval_target_selector_userInfo_repeats(ti: NSTimeInterval, aTarget: MemorySegment, aSelector: MemorySegment, userInfo: MemorySegment, yesOrNo: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("scheduledTimerWithTimeInterval:target:selector:userInfo:repeats:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ti, aTarget, aSelector, userInfo, yesOrNo) as MemorySegment
        }
        
        fun timerWithTimeInterval_repeats_block(interval: NSTimeInterval, repeats: BOOL, block: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("timerWithTimeInterval:repeats:block:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, interval, repeats, block) as MemorySegment
        }
        
        fun scheduledTimerWithTimeInterval_repeats_block(interval: NSTimeInterval, repeats: BOOL, block: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("scheduledTimerWithTimeInterval:repeats:block:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, interval, repeats, block) as MemorySegment
        }
        
    }
    
    fun initWithFireDate_interval_repeats_block(date: MemorySegment, interval: NSTimeInterval, repeats: BOOL, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFireDate:interval:repeats:block:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, interval, repeats, block) as MemorySegment
    }
    
    fun initWithFireDate_interval_target_selector_userInfo_repeats(date: MemorySegment, ti: NSTimeInterval, t: MemorySegment, s: MemorySegment, ui: MemorySegment, rep: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFireDate:interval:target:selector:userInfo:repeats:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, ti, t, s, ui, rep) as MemorySegment
    }
    
    fun fire(): Unit {
        val sel = ObjCRuntime.sel("fire")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property fireDate
    fun fireDate(): MemorySegment {
        val sel = ObjCRuntime.sel("fireDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFireDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFireDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeInterval
    fun timeInterval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
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
    
    // @property valid
    fun isValid(): BOOL {
        val sel = ObjCRuntime.sel("isValid")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property userInfo
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

