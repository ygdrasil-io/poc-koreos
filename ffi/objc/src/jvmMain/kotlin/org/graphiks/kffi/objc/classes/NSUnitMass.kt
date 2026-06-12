package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitMass
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitMass(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitMass") }
        
        fun kilograms(): MemorySegment {
            val sel = ObjCRuntime.sel("kilograms")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun grams(): MemorySegment {
            val sel = ObjCRuntime.sel("grams")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun decigrams(): MemorySegment {
            val sel = ObjCRuntime.sel("decigrams")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun centigrams(): MemorySegment {
            val sel = ObjCRuntime.sel("centigrams")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun milligrams(): MemorySegment {
            val sel = ObjCRuntime.sel("milligrams")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun micrograms(): MemorySegment {
            val sel = ObjCRuntime.sel("micrograms")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun nanograms(): MemorySegment {
            val sel = ObjCRuntime.sel("nanograms")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun picograms(): MemorySegment {
            val sel = ObjCRuntime.sel("picograms")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ounces(): MemorySegment {
            val sel = ObjCRuntime.sel("ounces")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun poundsMass(): MemorySegment {
            val sel = ObjCRuntime.sel("poundsMass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun stones(): MemorySegment {
            val sel = ObjCRuntime.sel("stones")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun metricTons(): MemorySegment {
            val sel = ObjCRuntime.sel("metricTons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun shortTons(): MemorySegment {
            val sel = ObjCRuntime.sel("shortTons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun carats(): MemorySegment {
            val sel = ObjCRuntime.sel("carats")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ouncesTroy(): MemorySegment {
            val sel = ObjCRuntime.sel("ouncesTroy")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun slugs(): MemorySegment {
            val sel = ObjCRuntime.sel("slugs")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property kilograms
    fun kilograms(): MemorySegment {
        val sel = ObjCRuntime.sel("kilograms")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property grams
    fun grams(): MemorySegment {
        val sel = ObjCRuntime.sel("grams")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property decigrams
    fun decigrams(): MemorySegment {
        val sel = ObjCRuntime.sel("decigrams")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property centigrams
    fun centigrams(): MemorySegment {
        val sel = ObjCRuntime.sel("centigrams")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property milligrams
    fun milligrams(): MemorySegment {
        val sel = ObjCRuntime.sel("milligrams")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property micrograms
    fun micrograms(): MemorySegment {
        val sel = ObjCRuntime.sel("micrograms")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nanograms
    fun nanograms(): MemorySegment {
        val sel = ObjCRuntime.sel("nanograms")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property picograms
    fun picograms(): MemorySegment {
        val sel = ObjCRuntime.sel("picograms")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property ounces
    fun ounces(): MemorySegment {
        val sel = ObjCRuntime.sel("ounces")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property poundsMass
    fun poundsMass(): MemorySegment {
        val sel = ObjCRuntime.sel("poundsMass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property stones
    fun stones(): MemorySegment {
        val sel = ObjCRuntime.sel("stones")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property metricTons
    fun metricTons(): MemorySegment {
        val sel = ObjCRuntime.sel("metricTons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortTons
    fun shortTons(): MemorySegment {
        val sel = ObjCRuntime.sel("shortTons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property carats
    fun carats(): MemorySegment {
        val sel = ObjCRuntime.sel("carats")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property ouncesTroy
    fun ouncesTroy(): MemorySegment {
        val sel = ObjCRuntime.sel("ouncesTroy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property slugs
    fun slugs(): MemorySegment {
        val sel = ObjCRuntime.sel("slugs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

