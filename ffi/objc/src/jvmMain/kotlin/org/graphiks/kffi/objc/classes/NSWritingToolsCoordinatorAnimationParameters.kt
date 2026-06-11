/**
 * Kotlin/JVM wrapper for Objective-C class: NSWritingToolsCoordinatorAnimationParameters
 * Superclass: NSObject
 */
open class NSWritingToolsCoordinatorAnimationParameters(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWritingToolsCoordinatorAnimationParameters") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property duration
    fun duration(): CGFloat {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property delay
    fun delay(): CGFloat {
        val sel = ObjCRuntime.sel("delay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property progressHandler
    fun progressHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("progressHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setProgressHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProgressHandler:")
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
    
}

