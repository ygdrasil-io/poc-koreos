package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPortMessage
 * Superclass: NSObject
 */
open class NSPortMessage(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPortMessage") }
        
    }
    
    open fun initWithSendPort_receivePort_components(sendPort: MemorySegment, replyPort: MemorySegment, components: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSendPort:receivePort:components:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendPort, replyPort, components) as MemorySegment
    }
    
    open fun sendBeforeDate(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("sendBeforeDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    // @property components
    open fun components(): MemorySegment {
        val sel = ObjCRuntime.sel("components")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property receivePort
    open fun receivePort(): MemorySegment {
        val sel = ObjCRuntime.sel("receivePort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sendPort
    open fun sendPort(): MemorySegment {
        val sel = ObjCRuntime.sel("sendPort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property msgid
    open fun msgid(): uint32_t {
        val sel = ObjCRuntime.sel("msgid")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as uint32_t
    }
    open fun setMsgid(value: uint32_t) {
        val sel = ObjCRuntime.sel("setMsgid:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: localPort: MemorySegment
    // ivar: remotePort: MemorySegment
    // ivar: components: MemorySegment
    // ivar: msgid: uint32_t
    // ivar: reserved2: MemorySegment
    // ivar: reserved: MemorySegment
}

