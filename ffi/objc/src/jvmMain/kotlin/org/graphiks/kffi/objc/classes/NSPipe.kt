/**
 * Kotlin/JVM wrapper for Objective-C class: NSPipe
 * Superclass: NSObject
 */
open class NSPipe(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPipe") }
        
        fun pipe(): MemorySegment {
            val sel = ObjCRuntime.sel("pipe")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property fileHandleForReading
    fun fileHandleForReading(): MemorySegment {
        val sel = ObjCRuntime.sel("fileHandleForReading")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fileHandleForWriting
    fun fileHandleForWriting(): MemorySegment {
        val sel = ObjCRuntime.sel("fileHandleForWriting")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

