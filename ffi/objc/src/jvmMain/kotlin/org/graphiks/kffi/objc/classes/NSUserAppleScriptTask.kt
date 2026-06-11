/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserAppleScriptTask
 * Superclass: NSUserScriptTask
 */
open class NSUserAppleScriptTask(ptr: MemorySegment) : NSUserScriptTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserAppleScriptTask") }
        
    }
    
    fun executeWithAppleEvent_completionHandler(event: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("executeWithAppleEvent:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, event, handler)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _isParentDefaultTarget: BOOL
}

