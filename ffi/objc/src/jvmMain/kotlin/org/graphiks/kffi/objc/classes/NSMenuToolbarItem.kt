package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMenuToolbarItem
 * Superclass: NSToolbarItem
 */
open class NSMenuToolbarItem(ptr: MemorySegment) : NSToolbarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMenuToolbarItem") }
        
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsIndicator
    fun showsIndicator(): BOOL {
        val sel = ObjCRuntime.sel("showsIndicator")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsIndicator(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsIndicator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

