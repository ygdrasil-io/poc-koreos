package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitSpeed
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitSpeed(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitSpeed") }
        
        fun metersPerSecond(): MemorySegment {
            val sel = ObjCRuntime.sel("metersPerSecond")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilometersPerHour(): MemorySegment {
            val sel = ObjCRuntime.sel("kilometersPerHour")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun milesPerHour(): MemorySegment {
            val sel = ObjCRuntime.sel("milesPerHour")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun knots(): MemorySegment {
            val sel = ObjCRuntime.sel("knots")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property metersPerSecond
    fun metersPerSecond(): MemorySegment {
        val sel = ObjCRuntime.sel("metersPerSecond")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kilometersPerHour
    fun kilometersPerHour(): MemorySegment {
        val sel = ObjCRuntime.sel("kilometersPerHour")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property milesPerHour
    fun milesPerHour(): MemorySegment {
        val sel = ObjCRuntime.sel("milesPerHour")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property knots
    fun knots(): MemorySegment {
        val sel = ObjCRuntime.sel("knots")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

