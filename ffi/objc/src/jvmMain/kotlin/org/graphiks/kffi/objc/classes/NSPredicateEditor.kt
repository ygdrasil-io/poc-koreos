package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPredicateEditor
 * Superclass: NSRuleEditor
 */
open class NSPredicateEditor(ptr: MemorySegment) : NSRuleEditor(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPredicateEditor") }
        
    }
    
    // @property rowTemplates
    /** @return NSArray<NSPredicateEditorRowTemplate *> * */
    fun rowTemplates(): MemorySegment {
        val sel = ObjCRuntime.sel("rowTemplates")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRowTemplates(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRowTemplates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

