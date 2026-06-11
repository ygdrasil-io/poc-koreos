/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitElectricCharge
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitElectricCharge(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitElectricCharge") }
        
        fun coulombs(): MemorySegment {
            val sel = ObjCRuntime.sel("coulombs")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun megaampereHours(): MemorySegment {
            val sel = ObjCRuntime.sel("megaampereHours")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kiloampereHours(): MemorySegment {
            val sel = ObjCRuntime.sel("kiloampereHours")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ampereHours(): MemorySegment {
            val sel = ObjCRuntime.sel("ampereHours")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun milliampereHours(): MemorySegment {
            val sel = ObjCRuntime.sel("milliampereHours")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun microampereHours(): MemorySegment {
            val sel = ObjCRuntime.sel("microampereHours")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property coulombs
    fun coulombs(): MemorySegment {
        val sel = ObjCRuntime.sel("coulombs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property megaampereHours
    fun megaampereHours(): MemorySegment {
        val sel = ObjCRuntime.sel("megaampereHours")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kiloampereHours
    fun kiloampereHours(): MemorySegment {
        val sel = ObjCRuntime.sel("kiloampereHours")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property ampereHours
    fun ampereHours(): MemorySegment {
        val sel = ObjCRuntime.sel("ampereHours")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property milliampereHours
    fun milliampereHours(): MemorySegment {
        val sel = ObjCRuntime.sel("milliampereHours")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property microampereHours
    fun microampereHours(): MemorySegment {
        val sel = ObjCRuntime.sel("microampereHours")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

