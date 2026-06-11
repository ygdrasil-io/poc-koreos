/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitTemperature
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitTemperature(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitTemperature") }
        
        fun kelvin(): MemorySegment {
            val sel = ObjCRuntime.sel("kelvin")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun celsius(): MemorySegment {
            val sel = ObjCRuntime.sel("celsius")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun fahrenheit(): MemorySegment {
            val sel = ObjCRuntime.sel("fahrenheit")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property kelvin
    fun kelvin(): MemorySegment {
        val sel = ObjCRuntime.sel("kelvin")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property celsius
    fun celsius(): MemorySegment {
        val sel = ObjCRuntime.sel("celsius")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fahrenheit
    fun fahrenheit(): MemorySegment {
        val sel = ObjCRuntime.sel("fahrenheit")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

