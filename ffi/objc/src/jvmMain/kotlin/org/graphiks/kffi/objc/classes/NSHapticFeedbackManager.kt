/**
 * Kotlin/JVM wrapper for Objective-C class: NSHapticFeedbackManager
 * Superclass: NSObject
 */
open class NSHapticFeedbackManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHapticFeedbackManager") }
        
        /** @return id<NSHapticFeedbackPerformer> */
        fun defaultPerformer(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultPerformer")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property defaultPerformer
    /** @return id<NSHapticFeedbackPerformer> */
    fun defaultPerformer(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultPerformer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

