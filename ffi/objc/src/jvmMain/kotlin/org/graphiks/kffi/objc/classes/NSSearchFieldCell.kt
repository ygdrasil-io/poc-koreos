package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSearchFieldCell
 * Superclass: NSTextFieldCell
 */
open class NSSearchFieldCell(ptr: MemorySegment) : NSTextFieldCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSearchFieldCell") }
        
    }
    
    override fun `initTextCell`(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `initTextCell`(string: String): MemorySegment = initTextCell(ObjCRuntime.newNSString(Arena.global(), string))
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    override fun `initImageCell`(image: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initImageCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, image) as MemorySegment
    }
    
    fun resetSearchButtonCell(): Unit {
        val sel = ObjCRuntime.sel("resetSearchButtonCell")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resetCancelButtonCell(): Unit {
        val sel = ObjCRuntime.sel("resetCancelButtonCell")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun searchTextRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("searchTextRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun searchButtonRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("searchButtonRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun cancelButtonRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("cancelButtonRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    // @property searchButtonCell
    fun searchButtonCell(): MemorySegment {
        val sel = ObjCRuntime.sel("searchButtonCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSearchButtonCell(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSearchButtonCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cancelButtonCell
    fun cancelButtonCell(): MemorySegment {
        val sel = ObjCRuntime.sel("cancelButtonCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCancelButtonCell(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCancelButtonCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property searchMenuTemplate
    fun searchMenuTemplate(): MemorySegment {
        val sel = ObjCRuntime.sel("searchMenuTemplate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSearchMenuTemplate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSearchMenuTemplate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sendsWholeSearchString
    fun sendsWholeSearchString(): BOOL {
        val sel = ObjCRuntime.sel("sendsWholeSearchString")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSendsWholeSearchString(value: BOOL) {
        val sel = ObjCRuntime.sel("setSendsWholeSearchString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumRecents
    fun maximumRecents(): NSInteger {
        val sel = ObjCRuntime.sel("maximumRecents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaximumRecents(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaximumRecents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property recentSearches
    /** @return NSArray<NSString *> * */
    fun recentSearches(): MemorySegment {
        val sel = ObjCRuntime.sel("recentSearches")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRecentSearches(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRecentSearches:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property recentsAutosaveName
    fun recentsAutosaveName(): NSSearchFieldRecentsAutosaveName {
        val sel = ObjCRuntime.sel("recentsAutosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSearchFieldRecentsAutosaveName
    }
    fun setRecentsAutosaveName(value: NSSearchFieldRecentsAutosaveName) {
        val sel = ObjCRuntime.sel("setRecentsAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sendsSearchStringImmediately
    fun sendsSearchStringImmediately(): BOOL {
        val sel = ObjCRuntime.sel("sendsSearchStringImmediately")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSendsSearchStringImmediately(value: BOOL) {
        val sel = ObjCRuntime.sel("setSendsSearchStringImmediately:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

