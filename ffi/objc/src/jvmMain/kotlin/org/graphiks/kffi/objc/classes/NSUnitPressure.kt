package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitPressure
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitPressure(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitPressure") }
        
        fun newtonsPerMetersSquared(): MemorySegment {
            val sel = ObjCRuntime.sel("newtonsPerMetersSquared")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gigapascals(): MemorySegment {
            val sel = ObjCRuntime.sel("gigapascals")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun megapascals(): MemorySegment {
            val sel = ObjCRuntime.sel("megapascals")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilopascals(): MemorySegment {
            val sel = ObjCRuntime.sel("kilopascals")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hectopascals(): MemorySegment {
            val sel = ObjCRuntime.sel("hectopascals")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun inchesOfMercury(): MemorySegment {
            val sel = ObjCRuntime.sel("inchesOfMercury")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun bars(): MemorySegment {
            val sel = ObjCRuntime.sel("bars")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun millibars(): MemorySegment {
            val sel = ObjCRuntime.sel("millibars")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun millimetersOfMercury(): MemorySegment {
            val sel = ObjCRuntime.sel("millimetersOfMercury")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun poundsForcePerSquareInch(): MemorySegment {
            val sel = ObjCRuntime.sel("poundsForcePerSquareInch")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property newtonsPerMetersSquared
    fun newtonsPerMetersSquared(): MemorySegment {
        val sel = ObjCRuntime.sel("newtonsPerMetersSquared")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property gigapascals
    fun gigapascals(): MemorySegment {
        val sel = ObjCRuntime.sel("gigapascals")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property megapascals
    fun megapascals(): MemorySegment {
        val sel = ObjCRuntime.sel("megapascals")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kilopascals
    fun kilopascals(): MemorySegment {
        val sel = ObjCRuntime.sel("kilopascals")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hectopascals
    fun hectopascals(): MemorySegment {
        val sel = ObjCRuntime.sel("hectopascals")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property inchesOfMercury
    fun inchesOfMercury(): MemorySegment {
        val sel = ObjCRuntime.sel("inchesOfMercury")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bars
    fun bars(): MemorySegment {
        val sel = ObjCRuntime.sel("bars")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property millibars
    fun millibars(): MemorySegment {
        val sel = ObjCRuntime.sel("millibars")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property millimetersOfMercury
    fun millimetersOfMercury(): MemorySegment {
        val sel = ObjCRuntime.sel("millimetersOfMercury")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property poundsForcePerSquareInch
    fun poundsForcePerSquareInch(): MemorySegment {
        val sel = ObjCRuntime.sel("poundsForcePerSquareInch")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

