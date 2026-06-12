package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSStatusBarButton
 * Superclass: NSButton
 */
open class NSStatusBarButton(ptr: MemorySegment) : NSButton(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStatusBarButton") }
        
    }
    
    // @property appearsDisabled
    fun appearsDisabled(): BOOL {
        val sel = ObjCRuntime.sel("appearsDisabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAppearsDisabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setAppearsDisabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

