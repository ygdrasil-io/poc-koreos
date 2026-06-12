package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSHelpManager
 * Superclass: NSObject
 */
open class NSHelpManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHelpManager") }
        
        open fun sharedHelpManager(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedHelpManager")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun isContextHelpModeActive(): BOOL {
            val sel = ObjCRuntime.sel("isContextHelpModeActive")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun setContextHelpModeActive(contextHelpModeActive: BOOL): Unit {
            val sel = ObjCRuntime.sel("setContextHelpModeActive:")
            ObjCRuntime.msgSend(null, _class, sel, contextHelpModeActive)
        }
        
    }
    
    open fun setContextHelp_forObject(attrString: MemorySegment, `object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setContextHelp:forObject:")
        ObjCRuntime.msgSend(null, ptr, sel, attrString, `object`)
    }
    
    open fun removeContextHelpForObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeContextHelpForObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    open fun contextHelpForObject(`object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("contextHelpForObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
    }
    
    open fun showContextHelpForObject_locationHint(`object`: MemorySegment, pt: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("showContextHelpForObject:locationHint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`, ObjCRuntime.ObjCStructArg(pt, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    open fun openHelpAnchor_inBook(anchor: NSHelpAnchorName, book: NSHelpBookName): Unit {
        val sel = ObjCRuntime.sel("openHelpAnchor:inBook:")
        ObjCRuntime.msgSend(null, ptr, sel, anchor, book)
    }
    
    open fun findString_inBook(query: MemorySegment, book: NSHelpBookName): Unit {
        val sel = ObjCRuntime.sel("findString:inBook:")
        ObjCRuntime.msgSend(null, ptr, sel, query, book)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun findString_inBook(query: String, book: NSHelpBookName): Unit = findString_inBook(ObjCRuntime.newNSString(Arena.global(), query), book)
    
    open fun registerBooksInBundle(bundle: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerBooksInBundle:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, bundle) as BOOL
    }
    
    // @property sharedHelpManager
}

