package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTrackingSeparatorToolbarItem
 * Superclass: NSToolbarItem
 */
open class NSTrackingSeparatorToolbarItem(ptr: MemorySegment) : NSToolbarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTrackingSeparatorToolbarItem") }
        
        fun trackingSeparatorToolbarItemWithIdentifier_splitView_dividerIndex(identifier: NSToolbarItemIdentifier, splitView: MemorySegment, dividerIndex: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("trackingSeparatorToolbarItemWithIdentifier:splitView:dividerIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, splitView, dividerIndex) as MemorySegment
        }
        
    }
    
    // @property splitView
    fun splitView(): MemorySegment {
        val sel = ObjCRuntime.sel("splitView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSplitView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSplitView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dividerIndex
    fun dividerIndex(): NSInteger {
        val sel = ObjCRuntime.sel("dividerIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setDividerIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setDividerIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

