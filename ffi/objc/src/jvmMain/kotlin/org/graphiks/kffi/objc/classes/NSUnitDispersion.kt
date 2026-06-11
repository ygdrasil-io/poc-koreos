/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitDispersion
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitDispersion(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitDispersion") }
        
        fun partsPerMillion(): MemorySegment {
            val sel = ObjCRuntime.sel("partsPerMillion")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property partsPerMillion
    fun partsPerMillion(): MemorySegment {
        val sel = ObjCRuntime.sel("partsPerMillion")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

