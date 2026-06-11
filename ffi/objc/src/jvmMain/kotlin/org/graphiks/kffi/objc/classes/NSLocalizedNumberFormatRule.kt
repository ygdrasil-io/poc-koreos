/**
 * Kotlin/JVM wrapper for Objective-C class: NSLocalizedNumberFormatRule
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSLocalizedNumberFormatRule(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLocalizedNumberFormatRule") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun automatic(): MemorySegment {
            val sel = ObjCRuntime.sel("automatic")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

