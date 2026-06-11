/**
 * Kotlin/JVM wrapper for Objective-C class: NSFileSecurity
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSFileSecurity(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFileSecurity") }
        
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
}

