package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMediaLibraryBrowserController
 * Superclass: NSObject
 */
open class NSMediaLibraryBrowserController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMediaLibraryBrowserController") }
        
        open fun sharedMediaLibraryBrowserController(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedMediaLibraryBrowserController")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun togglePanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("togglePanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property sharedMediaLibraryBrowserController
    open fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frame
    open fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    open fun setFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property mediaLibraries
    open fun mediaLibraries(): NSMediaLibrary {
        val sel = ObjCRuntime.sel("mediaLibraries")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMediaLibrary
    }
    open fun setMediaLibraries(value: NSMediaLibrary) {
        val sel = ObjCRuntime.sel("setMediaLibraries:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

