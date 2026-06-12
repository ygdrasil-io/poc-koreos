package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSNull
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSNull(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNull") }
        
        open fun null(): MemorySegment {
            val sel = ObjCRuntime.sel("null")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
}

// ── Category: CAActionAdditions on NSNull ─────────────────────────────────────────

