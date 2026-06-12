package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDraggingImageComponent
 * Superclass: NSObject
 */
open class NSDraggingImageComponent(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDraggingImageComponent") }
        
        open fun draggingImageComponentWithKey(key: NSDraggingImageComponentKey): MemorySegment {
            val sel = ObjCRuntime.sel("draggingImageComponentWithKey:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key) as MemorySegment
        }
        
    }
    
    open fun initWithKey(key: NSDraggingImageComponentKey): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property key
    open fun key(): NSDraggingImageComponentKey {
        val sel = ObjCRuntime.sel("key")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDraggingImageComponentKey
    }
    open fun setKey(value: NSDraggingImageComponentKey) {
        val sel = ObjCRuntime.sel("setKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contents
    open fun contents(): MemorySegment {
        val sel = ObjCRuntime.sel("contents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setContents(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContents:")
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
    
}

