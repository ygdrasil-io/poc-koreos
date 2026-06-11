/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionDownloadTask
 * Superclass: NSURLSessionTask
 */
open class NSURLSessionDownloadTask(ptr: MemorySegment) : NSURLSessionTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionDownloadTask") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun cancelByProducingResumeData(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cancelByProducingResumeData:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

