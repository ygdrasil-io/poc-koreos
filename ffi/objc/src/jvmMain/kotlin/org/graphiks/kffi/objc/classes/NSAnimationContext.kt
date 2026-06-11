/**
 * Kotlin/JVM wrapper for Objective-C class: NSAnimationContext
 * Superclass: NSObject
 */
open class NSAnimationContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAnimationContext") }
        
        fun runAnimationGroup_completionHandler(changes: MemorySegment, completionHandler: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("runAnimationGroup:completionHandler:")
            ObjCRuntime.msgSend(null, _class, sel, changes, completionHandler)
        }
        
        fun runAnimationGroup(changes: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("runAnimationGroup:")
            ObjCRuntime.msgSend(null, _class, sel, changes)
        }
        
        fun beginGrouping(): Unit {
            val sel = ObjCRuntime.sel("beginGrouping")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        fun endGrouping(): Unit {
            val sel = ObjCRuntime.sel("endGrouping")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        fun currentContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property currentContext
    fun currentContext(): MemorySegment {
        val sel = ObjCRuntime.sel("currentContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property duration
    fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setDuration(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setDuration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timingFunction
    fun timingFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("timingFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTimingFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimingFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property completionHandler
    fun completionHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("completionHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCompletionHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsImplicitAnimation
    fun allowsImplicitAnimation(): BOOL {
        val sel = ObjCRuntime.sel("allowsImplicitAnimation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsImplicitAnimation(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsImplicitAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

