package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSClickGestureRecognizer
 * Superclass: NSGestureRecognizer
 * Protocols: NSCoding
 */
open class NSClickGestureRecognizer(ptr: MemorySegment) : NSGestureRecognizer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSClickGestureRecognizer") }
        
    }
    
    // @property buttonMask
    fun buttonMask(): NSUInteger {
        val sel = ObjCRuntime.sel("buttonMask")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setButtonMask(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setButtonMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfClicksRequired
    fun numberOfClicksRequired(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfClicksRequired")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfClicksRequired(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfClicksRequired:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfTouchesRequired
    fun numberOfTouchesRequired(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfTouchesRequired")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfTouchesRequired(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfTouchesRequired:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

