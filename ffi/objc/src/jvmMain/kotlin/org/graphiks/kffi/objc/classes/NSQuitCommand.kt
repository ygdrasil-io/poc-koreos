/**
 * Kotlin/JVM wrapper for Objective-C class: NSQuitCommand
 * Superclass: NSScriptCommand
 */
open class NSQuitCommand(ptr: MemorySegment) : NSScriptCommand(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSQuitCommand") }
        
    }
    
    // @property saveOptions
    fun saveOptions(): NSSaveOptions {
        val sel = ObjCRuntime.sel("saveOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSaveOptions
    }
    
}

