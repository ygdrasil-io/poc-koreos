package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSHapticFeedbackManager
 * Superclass: NSObject
 */
open class NSHapticFeedbackManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHapticFeedbackManager") }
        
        /** @return id<NSHapticFeedbackPerformer> */
        open fun defaultPerformer(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultPerformer")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property defaultPerformer
    /** @return id<NSHapticFeedbackPerformer> */
}

