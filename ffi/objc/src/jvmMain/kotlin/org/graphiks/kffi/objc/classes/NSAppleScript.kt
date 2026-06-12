package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSAppleScript
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSAppleScript(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAppleScript") }
        
    }
    
    open fun initWithContentsOfURL_error(url: MemorySegment, errorInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, errorInfo) as MemorySegment
    }
    
    open fun initWithSource(source: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSource:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, source) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithSource(source: String): MemorySegment = initWithSource(ObjCRuntime.newNSString(Arena.global(), source))
    
    open fun compileAndReturnError(errorInfo: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("compileAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, errorInfo) as BOOL
    }
    
    open fun executeAndReturnError(errorInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("executeAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, errorInfo) as MemorySegment
    }
    
    open fun executeAppleEvent_error(event: MemorySegment, errorInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("executeAppleEvent:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event, errorInfo) as MemorySegment
    }
    
    // @property source
    open fun source(): MemorySegment {
        val sel = ObjCRuntime.sel("source")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun sourceAsString(): String = ObjCRuntime.toJavaString(source())
    
    // @property compiled
    open fun isCompiled(): BOOL {
        val sel = ObjCRuntime.sel("isCompiled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _source: MemorySegment
    // ivar: _compiledScriptID: Any
    // ivar: _reserved1: MemorySegment
    // ivar: _reserved2: MemorySegment
}

// ── Category: NSExtensions on NSAppleScript ─────────────────────────────────────────

fun NSAppleScript.richTextSource(): MemorySegment {
    val sel = ObjCRuntime.sel("richTextSource")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property richTextSource
    val sel = ObjCRuntime.sel("richTextSource")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

