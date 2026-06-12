package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSessionWebSocketTask
 * Superclass: NSURLSessionTask
 */
open class NSURLSessionWebSocketTask(ptr: MemorySegment) : NSURLSessionTask(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSessionWebSocketTask") }
        
        override fun `new`(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun sendMessage_completionHandler(message: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("sendMessage:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, message, completionHandler)
    }
    
    fun receiveMessageWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("receiveMessageWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun sendPingWithPongReceiveHandler(pongReceiveHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("sendPingWithPongReceiveHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, pongReceiveHandler)
    }
    
    fun cancelWithCloseCode_reason(closeCode: NSURLSessionWebSocketCloseCode, reason: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cancelWithCloseCode:reason:")
        ObjCRuntime.msgSend(null, ptr, sel, closeCode, reason)
    }
    
    override fun `init`(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property maximumMessageSize
    fun maximumMessageSize(): NSInteger {
        val sel = ObjCRuntime.sel("maximumMessageSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaximumMessageSize(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaximumMessageSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property closeCode
    fun closeCode(): NSURLSessionWebSocketCloseCode {
        val sel = ObjCRuntime.sel("closeCode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLSessionWebSocketCloseCode
    }
    
    // @property closeReason
    fun closeReason(): MemorySegment {
        val sel = ObjCRuntime.sel("closeReason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

