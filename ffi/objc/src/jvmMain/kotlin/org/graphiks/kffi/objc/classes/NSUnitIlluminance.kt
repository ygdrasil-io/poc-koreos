/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitIlluminance
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitIlluminance(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitIlluminance") }
        
        fun lux(): MemorySegment {
            val sel = ObjCRuntime.sel("lux")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property lux
    fun lux(): MemorySegment {
        val sel = ObjCRuntime.sel("lux")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

