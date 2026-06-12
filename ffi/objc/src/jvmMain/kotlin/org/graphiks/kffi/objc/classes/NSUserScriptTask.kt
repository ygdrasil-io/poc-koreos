package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserScriptTask
 * Superclass: NSObject
 */
open class NSUserScriptTask(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserScriptTask") }
        
    }
    
    open fun initWithURL_error(url: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, error) as MemorySegment
    }
    
    open fun executeWithCompletionHandler(handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("executeWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, handler)
    }
    
    // @property scriptURL
    open fun scriptURL(): MemorySegment {
        val sel = ObjCRuntime.sel("scriptURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _scriptURL: MemorySegment
    // ivar: _connection: MemorySegment
    // ivar: _hasExeced: BOOL
    // ivar: _hasTerminated: BOOL
    // ivar: _stdin: MemorySegment
    // ivar: _stdout: MemorySegment
    // ivar: _stderr: MemorySegment
}

