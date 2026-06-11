/**
 * Kotlin/JVM wrapper for Objective-C class: NSDistantObjectRequest
 * Superclass: NSObject
 */
open class NSDistantObjectRequest(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDistantObjectRequest") }
        
    }
    
    fun replyWithException(exception: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replyWithException:")
        ObjCRuntime.msgSend(null, ptr, sel, exception)
    }
    
    // @property invocation
    fun invocation(): MemorySegment {
        val sel = ObjCRuntime.sel("invocation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property connection
    fun connection(): MemorySegment {
        val sel = ObjCRuntime.sel("connection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property conversation
    fun conversation(): MemorySegment {
        val sel = ObjCRuntime.sel("conversation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

