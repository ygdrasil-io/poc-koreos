/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitLength
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitLength(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitLength") }
        
        fun megameters(): MemorySegment {
            val sel = ObjCRuntime.sel("megameters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilometers(): MemorySegment {
            val sel = ObjCRuntime.sel("kilometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hectometers(): MemorySegment {
            val sel = ObjCRuntime.sel("hectometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun decameters(): MemorySegment {
            val sel = ObjCRuntime.sel("decameters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun meters(): MemorySegment {
            val sel = ObjCRuntime.sel("meters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun decimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("decimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun centimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("centimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun millimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("millimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun micrometers(): MemorySegment {
            val sel = ObjCRuntime.sel("micrometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun nanometers(): MemorySegment {
            val sel = ObjCRuntime.sel("nanometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun picometers(): MemorySegment {
            val sel = ObjCRuntime.sel("picometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun inches(): MemorySegment {
            val sel = ObjCRuntime.sel("inches")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun feet(): MemorySegment {
            val sel = ObjCRuntime.sel("feet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yards(): MemorySegment {
            val sel = ObjCRuntime.sel("yards")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun miles(): MemorySegment {
            val sel = ObjCRuntime.sel("miles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun scandinavianMiles(): MemorySegment {
            val sel = ObjCRuntime.sel("scandinavianMiles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun lightyears(): MemorySegment {
            val sel = ObjCRuntime.sel("lightyears")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun nauticalMiles(): MemorySegment {
            val sel = ObjCRuntime.sel("nauticalMiles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun fathoms(): MemorySegment {
            val sel = ObjCRuntime.sel("fathoms")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun furlongs(): MemorySegment {
            val sel = ObjCRuntime.sel("furlongs")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun astronomicalUnits(): MemorySegment {
            val sel = ObjCRuntime.sel("astronomicalUnits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun parsecs(): MemorySegment {
            val sel = ObjCRuntime.sel("parsecs")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property megameters
    fun megameters(): MemorySegment {
        val sel = ObjCRuntime.sel("megameters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kilometers
    fun kilometers(): MemorySegment {
        val sel = ObjCRuntime.sel("kilometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hectometers
    fun hectometers(): MemorySegment {
        val sel = ObjCRuntime.sel("hectometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property decameters
    fun decameters(): MemorySegment {
        val sel = ObjCRuntime.sel("decameters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property meters
    fun meters(): MemorySegment {
        val sel = ObjCRuntime.sel("meters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property decimeters
    fun decimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("decimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property centimeters
    fun centimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("centimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property millimeters
    fun millimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("millimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property micrometers
    fun micrometers(): MemorySegment {
        val sel = ObjCRuntime.sel("micrometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nanometers
    fun nanometers(): MemorySegment {
        val sel = ObjCRuntime.sel("nanometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property picometers
    fun picometers(): MemorySegment {
        val sel = ObjCRuntime.sel("picometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property inches
    fun inches(): MemorySegment {
        val sel = ObjCRuntime.sel("inches")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property feet
    fun feet(): MemorySegment {
        val sel = ObjCRuntime.sel("feet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property yards
    fun yards(): MemorySegment {
        val sel = ObjCRuntime.sel("yards")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property miles
    fun miles(): MemorySegment {
        val sel = ObjCRuntime.sel("miles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property scandinavianMiles
    fun scandinavianMiles(): MemorySegment {
        val sel = ObjCRuntime.sel("scandinavianMiles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property lightyears
    fun lightyears(): MemorySegment {
        val sel = ObjCRuntime.sel("lightyears")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nauticalMiles
    fun nauticalMiles(): MemorySegment {
        val sel = ObjCRuntime.sel("nauticalMiles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fathoms
    fun fathoms(): MemorySegment {
        val sel = ObjCRuntime.sel("fathoms")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property furlongs
    fun furlongs(): MemorySegment {
        val sel = ObjCRuntime.sel("furlongs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property astronomicalUnits
    fun astronomicalUnits(): MemorySegment {
        val sel = ObjCRuntime.sel("astronomicalUnits")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property parsecs
    fun parsecs(): MemorySegment {
        val sel = ObjCRuntime.sel("parsecs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

