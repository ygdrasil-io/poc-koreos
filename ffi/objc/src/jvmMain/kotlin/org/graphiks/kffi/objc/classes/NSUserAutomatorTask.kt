/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserAutomatorTask
 * Superclass: NSUserScriptTask
 */
open class NSUserAutomatorTask(ptr: MemorySegment) : NSUserScriptTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserAutomatorTask") }
        
    }
    
    fun executeWithInput_completionHandler(input: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("executeWithInput:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, input, handler)
    }
    
    // @property variables
    /** @return NSDictionary<NSString *,id> * */
    fun variables(): MemorySegment {
        val sel = ObjCRuntime.sel("variables")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVariables(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVariables:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _variables: MemorySegment
}

