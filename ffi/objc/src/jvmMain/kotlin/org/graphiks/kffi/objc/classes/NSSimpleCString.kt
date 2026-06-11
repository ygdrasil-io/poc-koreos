/**
 * Kotlin/JVM wrapper for Objective-C class: NSSimpleCString
 * Superclass: NSString
 */
open class NSSimpleCString(ptr: MemorySegment) : NSString(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSimpleCString") }
        
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: bytes: MemorySegment
    // ivar: numBytes: Int
    // ivar: _unused: Int
}

