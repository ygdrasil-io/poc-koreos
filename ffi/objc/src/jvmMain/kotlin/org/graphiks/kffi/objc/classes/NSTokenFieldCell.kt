package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTokenFieldCell
 * Superclass: NSTextFieldCell
 */
open class NSTokenFieldCell(ptr: MemorySegment) : NSTextFieldCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTokenFieldCell") }
        
        fun defaultCompletionDelay(): NSTimeInterval {
            val sel = ObjCRuntime.sel("defaultCompletionDelay")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as NSTimeInterval
        }
        
        fun defaultTokenizingCharacterSet(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultTokenizingCharacterSet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property tokenStyle
    fun tokenStyle(): NSTokenStyle {
        val sel = ObjCRuntime.sel("tokenStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTokenStyle
    }
    fun setTokenStyle(value: NSTokenStyle) {
        val sel = ObjCRuntime.sel("setTokenStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property completionDelay
    fun completionDelay(): NSTimeInterval {
        val sel = ObjCRuntime.sel("completionDelay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setCompletionDelay(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setCompletionDelay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultCompletionDelay
    fun defaultCompletionDelay(): NSTimeInterval {
        val sel = ObjCRuntime.sel("defaultCompletionDelay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property tokenizingCharacterSet
    fun tokenizingCharacterSet(): MemorySegment {
        val sel = ObjCRuntime.sel("tokenizingCharacterSet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTokenizingCharacterSet(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTokenizingCharacterSet:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultTokenizingCharacterSet
    fun defaultTokenizingCharacterSet(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultTokenizingCharacterSet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSTokenFieldCellDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

