package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPositionalSpecifier
 * Superclass: NSObject
 */
open class NSPositionalSpecifier(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPositionalSpecifier") }
        
    }
    
    open fun initWithPosition_objectSpecifier(position: NSInsertionPosition, specifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPosition:objectSpecifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, position, specifier) as MemorySegment
    }
    
    open fun setInsertionClassDescription(classDescription: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setInsertionClassDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, classDescription)
    }
    
    open fun evaluate(): Unit {
        val sel = ObjCRuntime.sel("evaluate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property position
    open fun position(): NSInsertionPosition {
        val sel = ObjCRuntime.sel("position")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSInsertionPosition
    }
    
    // @property objectSpecifier
    open fun objectSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("objectSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property insertionContainer
    open fun insertionContainer(): MemorySegment {
        val sel = ObjCRuntime.sel("insertionContainer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property insertionKey
    open fun insertionKey(): MemorySegment {
        val sel = ObjCRuntime.sel("insertionKey")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun insertionKeyAsString(): String = ObjCRuntime.toJavaString(insertionKey())
    
    // @property insertionIndex
    open fun insertionIndex(): NSInteger {
        val sel = ObjCRuntime.sel("insertionIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property insertionReplaces
    open fun insertionReplaces(): BOOL {
        val sel = ObjCRuntime.sel("insertionReplaces")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _specifier: MemorySegment
    // ivar: _unadjustedPosition: NSInsertionPosition
    // ivar: _insertionClassDescription: MemorySegment
    // ivar: _moreVars: MemorySegment
    // ivar: _reserved0: MemorySegment
}

