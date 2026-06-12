package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutSpacing
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutSpacing(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutSpacing") }
        
        open fun flexibleSpacing(flexibleSpacing: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("flexibleSpacing:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, flexibleSpacing) as MemorySegment
        }
        
        open fun fixedSpacing(fixedSpacing: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fixedSpacing:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fixedSpacing) as MemorySegment
        }
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property spacing
    open fun spacing(): CGFloat {
        val sel = ObjCRuntime.sel("spacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property isFlexibleSpacing
    open fun isFlexibleSpacing(): BOOL {
        val sel = ObjCRuntime.sel("isFlexibleSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isFixedSpacing
    open fun isFixedSpacing(): BOOL {
        val sel = ObjCRuntime.sel("isFixedSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

