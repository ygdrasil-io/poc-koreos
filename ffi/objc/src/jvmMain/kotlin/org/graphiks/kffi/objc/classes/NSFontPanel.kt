package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSFontPanel
 * Superclass: NSPanel
 */
open class NSFontPanel(ptr: MemorySegment) : NSPanel(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFontPanel") }
        
        fun sharedFontPanel(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedFontPanel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun sharedFontPanelExists(): BOOL {
            val sel = ObjCRuntime.sel("sharedFontPanelExists")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun setPanelFont_isMultiple(fontObj: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setPanelFont:isMultiple:")
        ObjCRuntime.msgSend(null, ptr, sel, fontObj, flag)
    }
    
    fun panelConvertFont(fontObj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("panelConvertFont:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj) as MemorySegment
    }
    
    fun reloadDefaultFontFamilies(): Unit {
        val sel = ObjCRuntime.sel("reloadDefaultFontFamilies")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property sharedFontPanel
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property worksWhenModal
    override fun `worksWhenModal`(): BOOL {
        val sel = ObjCRuntime.sel("worksWhenModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    override fun `setWorksWhenModal`(value: BOOL) {
        val sel = ObjCRuntime.sel("setWorksWhenModal:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enabled
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

