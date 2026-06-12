package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDirectoryEnumerator
 * Superclass: NSEnumerator
 */
open class NSDirectoryEnumerator(ptr: MemorySegment) : NSEnumerator(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDirectoryEnumerator") }
        
    }
    
    fun skipDescendents(): Unit {
        val sel = ObjCRuntime.sel("skipDescendents")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun skipDescendants(): Unit {
        val sel = ObjCRuntime.sel("skipDescendants")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property fileAttributes
    /** @return NSDictionary<NSFileAttributeKey,id> * */
    fun fileAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("fileAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property directoryAttributes
    /** @return NSDictionary<NSFileAttributeKey,id> * */
    fun directoryAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("directoryAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property isEnumeratingDirectoryPostOrder
    fun isEnumeratingDirectoryPostOrder(): BOOL {
        val sel = ObjCRuntime.sel("isEnumeratingDirectoryPostOrder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property level
    fun level(): NSUInteger {
        val sel = ObjCRuntime.sel("level")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

