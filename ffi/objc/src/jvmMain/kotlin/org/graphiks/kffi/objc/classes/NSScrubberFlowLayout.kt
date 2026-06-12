package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberFlowLayout
 * Superclass: NSScrubberLayout
 */
open class NSScrubberFlowLayout(ptr: MemorySegment) : NSScrubberLayout(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberFlowLayout") }
        
    }
    
    fun invalidateLayoutForItemsAtIndexes(invalidItemIndexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateLayoutForItemsAtIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, invalidItemIndexes)
    }
    
    // @property itemSpacing
    fun itemSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("itemSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setItemSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setItemSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemSize
    fun itemSize(): NSSize {
        val sel = ObjCRuntime.sel("itemSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setItemSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setItemSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
}

