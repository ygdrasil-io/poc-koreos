/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionUploadTask
 * Superclass: NSURLSessionDataTask
 */
open class NSURLSessionUploadTask(ptr: MemorySegment) : NSURLSessionDataTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionUploadTask") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun cancelByProducingResumeData(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cancelByProducingResumeData:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
}

