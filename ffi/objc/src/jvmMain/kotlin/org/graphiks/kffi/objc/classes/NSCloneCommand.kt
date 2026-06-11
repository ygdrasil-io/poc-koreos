/**
 * Kotlin/JVM wrapper for Objective-C class: NSCloneCommand
 * Superclass: NSScriptCommand
 */
open class NSCloneCommand(ptr: MemorySegment) : NSScriptCommand(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCloneCommand") }
        
    }
    
    fun setReceiversSpecifier(receiversRef: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setReceiversSpecifier:")
        ObjCRuntime.msgSend(null, ptr, sel, receiversRef)
    }
    
    // @property keySpecifier
    fun keySpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("keySpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _keySpecifier: MemorySegment
}

