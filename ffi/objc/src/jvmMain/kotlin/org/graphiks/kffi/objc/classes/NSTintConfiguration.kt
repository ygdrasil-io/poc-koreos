package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTintConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSTintConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTintConfiguration") }
        
        open fun tintConfigurationWithPreferredColor(color: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tintConfigurationWithPreferredColor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, color) as MemorySegment
        }
        
        open fun tintConfigurationWithFixedColor(color: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tintConfigurationWithFixedColor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, color) as MemorySegment
        }
        
        open fun defaultTintConfiguration(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultTintConfiguration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun monochromeTintConfiguration(): MemorySegment {
            val sel = ObjCRuntime.sel("monochromeTintConfiguration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property defaultTintConfiguration
    }
    
    // @property monochromeTintConfiguration
    }
    
    // @property baseTintColor
    open fun baseTintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("baseTintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property equivalentContentTintColor
    open fun equivalentContentTintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("equivalentContentTintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property adaptsToUserAccentColor
    open fun adaptsToUserAccentColor(): BOOL {
        val sel = ObjCRuntime.sel("adaptsToUserAccentColor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

