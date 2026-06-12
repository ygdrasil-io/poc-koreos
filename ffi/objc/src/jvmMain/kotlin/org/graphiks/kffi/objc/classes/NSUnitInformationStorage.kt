package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitInformationStorage
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitInformationStorage(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitInformationStorage") }
        
        fun bytes(): MemorySegment {
            val sel = ObjCRuntime.sel("bytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun bits(): MemorySegment {
            val sel = ObjCRuntime.sel("bits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun nibbles(): MemorySegment {
            val sel = ObjCRuntime.sel("nibbles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yottabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("yottabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun zettabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("zettabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun exabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("exabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun petabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("petabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun terabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("terabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gigabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("gigabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun megabytes(): MemorySegment {
            val sel = ObjCRuntime.sel("megabytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilobytes(): MemorySegment {
            val sel = ObjCRuntime.sel("kilobytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yottabits(): MemorySegment {
            val sel = ObjCRuntime.sel("yottabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun zettabits(): MemorySegment {
            val sel = ObjCRuntime.sel("zettabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun exabits(): MemorySegment {
            val sel = ObjCRuntime.sel("exabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun petabits(): MemorySegment {
            val sel = ObjCRuntime.sel("petabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun terabits(): MemorySegment {
            val sel = ObjCRuntime.sel("terabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gigabits(): MemorySegment {
            val sel = ObjCRuntime.sel("gigabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun megabits(): MemorySegment {
            val sel = ObjCRuntime.sel("megabits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilobits(): MemorySegment {
            val sel = ObjCRuntime.sel("kilobits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yobibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("yobibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun zebibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("zebibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun exbibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("exbibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun pebibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("pebibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun tebibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("tebibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gibibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("gibibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun mebibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("mebibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kibibytes(): MemorySegment {
            val sel = ObjCRuntime.sel("kibibytes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yobibits(): MemorySegment {
            val sel = ObjCRuntime.sel("yobibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun zebibits(): MemorySegment {
            val sel = ObjCRuntime.sel("zebibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun exbibits(): MemorySegment {
            val sel = ObjCRuntime.sel("exbibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun pebibits(): MemorySegment {
            val sel = ObjCRuntime.sel("pebibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun tebibits(): MemorySegment {
            val sel = ObjCRuntime.sel("tebibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gibibits(): MemorySegment {
            val sel = ObjCRuntime.sel("gibibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun mebibits(): MemorySegment {
            val sel = ObjCRuntime.sel("mebibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kibibits(): MemorySegment {
            val sel = ObjCRuntime.sel("kibibits")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property bytes
}

