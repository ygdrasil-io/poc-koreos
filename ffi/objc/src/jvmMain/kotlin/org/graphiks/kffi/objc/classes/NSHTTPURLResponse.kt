package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSHTTPURLResponse
 * Superclass: NSURLResponse
 */
open class NSHTTPURLResponse(ptr: MemorySegment) : NSURLResponse(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHTTPURLResponse") }
        
        fun localizedStringForStatusCode(statusCode: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("localizedStringForStatusCode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, statusCode) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedStringForStatusCodeAsString(statusCode: NSInteger): String = ObjCRuntime.toJavaString(localizedStringForStatusCode(statusCode))
        
    }
    
    fun initWithURL_statusCode_HTTPVersion_headerFields(url: MemorySegment, statusCode: NSInteger, HTTPVersion: MemorySegment, headerFields: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:statusCode:HTTPVersion:headerFields:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, statusCode, HTTPVersion, headerFields) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithURL_statusCode_HTTPVersion_headerFields(url: MemorySegment, statusCode: NSInteger, HTTPVersion: String, headerFields: MemorySegment): MemorySegment = initWithURL_statusCode_HTTPVersion_headerFields(url, statusCode, ObjCRuntime.newNSString(Arena.global(), HTTPVersion), headerFields)
    
    fun valueForHTTPHeaderField(field: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("valueForHTTPHeaderField:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, field) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun valueForHTTPHeaderFieldAsString(field: MemorySegment): String = ObjCRuntime.toJavaString(valueForHTTPHeaderField(field))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun valueForHTTPHeaderField(field: String): MemorySegment = valueForHTTPHeaderField(ObjCRuntime.newNSString(Arena.global(), field))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun valueForHTTPHeaderFieldAsString(field: String): String = ObjCRuntime.toJavaString(valueForHTTPHeaderField(ObjCRuntime.newNSString(Arena.global(), field)))
    
    // @property statusCode
    fun statusCode(): NSInteger {
        val sel = ObjCRuntime.sel("statusCode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property allHeaderFields
    fun allHeaderFields(): MemorySegment {
        val sel = ObjCRuntime.sel("allHeaderFields")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _httpInternal: MemorySegment
}

