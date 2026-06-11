/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitElectricPotentialDifference
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitElectricPotentialDifference(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitElectricPotentialDifference") }
        
        fun megavolts(): MemorySegment {
            val sel = ObjCRuntime.sel("megavolts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilovolts(): MemorySegment {
            val sel = ObjCRuntime.sel("kilovolts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun volts(): MemorySegment {
            val sel = ObjCRuntime.sel("volts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun millivolts(): MemorySegment {
            val sel = ObjCRuntime.sel("millivolts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun microvolts(): MemorySegment {
            val sel = ObjCRuntime.sel("microvolts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property megavolts
    fun megavolts(): MemorySegment {
        val sel = ObjCRuntime.sel("megavolts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kilovolts
    fun kilovolts(): MemorySegment {
        val sel = ObjCRuntime.sel("kilovolts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property volts
    fun volts(): MemorySegment {
        val sel = ObjCRuntime.sel("volts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property millivolts
    fun millivolts(): MemorySegment {
        val sel = ObjCRuntime.sel("millivolts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property microvolts
    fun microvolts(): MemorySegment {
        val sel = ObjCRuntime.sel("microvolts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

