package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitArea
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitArea(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitArea") }
        
        fun squareMegameters(): MemorySegment {
            val sel = ObjCRuntime.sel("squareMegameters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareKilometers(): MemorySegment {
            val sel = ObjCRuntime.sel("squareKilometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareMeters(): MemorySegment {
            val sel = ObjCRuntime.sel("squareMeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareCentimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("squareCentimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareMillimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("squareMillimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareMicrometers(): MemorySegment {
            val sel = ObjCRuntime.sel("squareMicrometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareNanometers(): MemorySegment {
            val sel = ObjCRuntime.sel("squareNanometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareInches(): MemorySegment {
            val sel = ObjCRuntime.sel("squareInches")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareFeet(): MemorySegment {
            val sel = ObjCRuntime.sel("squareFeet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareYards(): MemorySegment {
            val sel = ObjCRuntime.sel("squareYards")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun squareMiles(): MemorySegment {
            val sel = ObjCRuntime.sel("squareMiles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun acres(): MemorySegment {
            val sel = ObjCRuntime.sel("acres")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ares(): MemorySegment {
            val sel = ObjCRuntime.sel("ares")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hectares(): MemorySegment {
            val sel = ObjCRuntime.sel("hectares")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property squareMegameters
    }
    
    // @property squareKilometers
    }
    
    // @property squareMeters
    }
    
    // @property squareCentimeters
    }
    
    // @property squareMillimeters
    }
    
    // @property squareMicrometers
    }
    
    // @property squareNanometers
    }
    
    // @property squareInches
    }
    
    // @property squareFeet
    }
    
    // @property squareYards
    }
    
    // @property squareMiles
    }
    
    // @property acres
    }
    
    // @property ares
    }
    
    // @property hectares
    }
    
}

