/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserUnixTask
 * Superclass: NSUserScriptTask
 */
open class NSUserUnixTask(ptr: MemorySegment) : NSUserScriptTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserUnixTask") }
        
    }
    
    fun executeWithArguments_completionHandler(arguments: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("executeWithArguments:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, arguments, handler)
    }
    
    // @property standardInput
    fun standardInput(): MemorySegment {
        val sel = ObjCRuntime.sel("standardInput")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardInput(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardInput:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardOutput
    fun standardOutput(): MemorySegment {
        val sel = ObjCRuntime.sel("standardOutput")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardOutput(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardOutput:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardError
    fun standardError(): MemorySegment {
        val sel = ObjCRuntime.sel("standardError")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardError(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardError:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

