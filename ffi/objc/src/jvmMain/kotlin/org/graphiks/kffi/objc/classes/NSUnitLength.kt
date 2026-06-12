package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

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
    }
    
    // @property kilometers
    }
    
    // @property hectometers
    }
    
    // @property decameters
    }
    
    // @property meters
    }
    
    // @property decimeters
    }
    
    // @property centimeters
    }
    
    // @property millimeters
    }
    
    // @property micrometers
    }
    
    // @property nanometers
    }
    
    // @property picometers
    }
    
    // @property inches
    }
    
    // @property feet
    }
    
    // @property yards
    }
    
    // @property miles
    }
    
    // @property scandinavianMiles
    }
    
    // @property lightyears
    }
    
    // @property nauticalMiles
    }
    
    // @property fathoms
    }
    
    // @property furlongs
    }
    
    // @property astronomicalUnits
    }
    
    // @property parsecs
    }
    
}

