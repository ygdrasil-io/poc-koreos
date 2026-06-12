package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSearchField
 * Superclass: NSTextField
 */
open class NSSearchField(ptr: MemorySegment) : NSTextField(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSearchField") }
        
    }
    
    // @property searchTextBounds
    fun searchTextBounds(): NSRect {
        val sel = ObjCRuntime.sel("searchTextBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property searchButtonBounds
    fun searchButtonBounds(): NSRect {
        val sel = ObjCRuntime.sel("searchButtonBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property cancelButtonBounds
    fun cancelButtonBounds(): NSRect {
        val sel = ObjCRuntime.sel("cancelButtonBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
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
    
    // @property sendsSearchStringImmediately
    fun sendsSearchStringImmediately(): BOOL {
        val sel = ObjCRuntime.sel("sendsSearchStringImmediately")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSendsSearchStringImmediately(value: BOOL) {
        val sel = ObjCRuntime.sel("setSendsSearchStringImmediately:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSSearchFieldDelegate> */
    override fun `delegate`(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    override fun `setDelegate`(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSSearchField_Deprecated on NSSearchField ─────────────────────────────────────────

fun NSSearchField.rectForSearchTextWhenCentered(isCentered: BOOL): NSRect {
    val sel = ObjCRuntime.sel("rectForSearchTextWhenCentered:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, isCentered) as NSRect
}

fun NSSearchField.rectForSearchButtonWhenCentered(isCentered: BOOL): NSRect {
    val sel = ObjCRuntime.sel("rectForSearchButtonWhenCentered:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, isCentered) as NSRect
}

fun NSSearchField.rectForCancelButtonWhenCentered(isCentered: BOOL): NSRect {
    val sel = ObjCRuntime.sel("rectForCancelButtonWhenCentered:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, isCentered) as NSRect
}

fun NSSearchField.centersPlaceholder(): BOOL {
    val sel = ObjCRuntime.sel("centersPlaceholder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSSearchField.setCentersPlaceholder(centersPlaceholder: BOOL): Unit {
    val sel = ObjCRuntime.sel("setCentersPlaceholder:")
    ObjCRuntime.msgSend(null, ptr, sel, centersPlaceholder)
}

// @property centersPlaceholder
    val sel = ObjCRuntime.sel("centersPlaceholder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setCentersPlaceholder:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

