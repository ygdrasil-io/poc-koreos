package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRandomSpecifier
 * Superclass: NSScriptObjectSpecifier
 */
open class NSRandomSpecifier(ptr: MemorySegment) : NSScriptObjectSpecifier(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRandomSpecifier") }
        
    }
    
}

