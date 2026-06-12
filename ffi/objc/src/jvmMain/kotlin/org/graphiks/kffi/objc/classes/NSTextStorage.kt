package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextStorage
 * Superclass: NSMutableAttributedString
 * Protocols: NSSecureCoding
 */
open class NSTextStorage(ptr: MemorySegment) : NSMutableAttributedString(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextStorage") }
        
    }
    
    fun addLayoutManager(aLayoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, aLayoutManager)
    }
    
    fun removeLayoutManager(aLayoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, aLayoutManager)
    }
    
    fun edited_range_changeInLength(editedMask: NSTextStorageEditActions, editedRange: NSRange, delta: NSInteger): Unit {
        val sel = ObjCRuntime.sel("edited:range:changeInLength:")
        ObjCRuntime.msgSend(null, ptr, sel, editedMask, ObjCRuntime.ObjCStructArg(editedRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), delta)
    }
    
    fun processEditing(): Unit {
        val sel = ObjCRuntime.sel("processEditing")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidateAttributesInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("invalidateAttributesInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun ensureAttributesAreFixedInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("ensureAttributesAreFixedInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    // @property layoutManagers
    /** @return NSArray<NSLayoutManager *> * */
    fun layoutManagers(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutManagers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property editedMask
    fun editedMask(): NSTextStorageEditActions {
        val sel = ObjCRuntime.sel("editedMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextStorageEditActions
    }
    
    // @property editedRange
    fun editedRange(): NSRange {
        val sel = ObjCRuntime.sel("editedRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property changeInLength
    fun changeInLength(): NSInteger {
        val sel = ObjCRuntime.sel("changeInLength")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property delegate
    /** @return id<NSTextStorageDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fixesAttributesLazily
    fun fixesAttributesLazily(): BOOL {
        val sel = ObjCRuntime.sel("fixesAttributesLazily")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property textStorageObserver
    /** @return id<NSTextStorageObserving> */
    fun textStorageObserver(): MemorySegment {
        val sel = ObjCRuntime.sel("textStorageObserver")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextStorageObserver(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextStorageObserver:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: Scripting on NSTextStorage ─────────────────────────────────────────

/** @return NSArray<NSTextStorage *> * */
fun NSTextStorage.attributeRuns(): MemorySegment {
    val sel = ObjCRuntime.sel("attributeRuns")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setAttributeRuns(attributeRuns: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributeRuns:")
    ObjCRuntime.msgSend(null, ptr, sel, attributeRuns)
}

/** @return NSArray<NSTextStorage *> * */
fun NSTextStorage.paragraphs(): MemorySegment {
    val sel = ObjCRuntime.sel("paragraphs")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setParagraphs(paragraphs: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setParagraphs:")
    ObjCRuntime.msgSend(null, ptr, sel, paragraphs)
}

/** @return NSArray<NSTextStorage *> * */
fun NSTextStorage.words(): MemorySegment {
    val sel = ObjCRuntime.sel("words")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setWords(words: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setWords:")
    ObjCRuntime.msgSend(null, ptr, sel, words)
}

/** @return NSArray<NSTextStorage *> * */
fun NSTextStorage.characters(): MemorySegment {
    val sel = ObjCRuntime.sel("characters")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setCharacters(characters: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setCharacters:")
    ObjCRuntime.msgSend(null, ptr, sel, characters)
}

fun NSTextStorage.font(): MemorySegment {
    val sel = ObjCRuntime.sel("font")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setFont(font: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setFont:")
    ObjCRuntime.msgSend(null, ptr, sel, font)
}

fun NSTextStorage.foregroundColor(): MemorySegment {
    val sel = ObjCRuntime.sel("foregroundColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextStorage.setForegroundColor(foregroundColor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setForegroundColor:")
    ObjCRuntime.msgSend(null, ptr, sel, foregroundColor)
}

// @property attributeRuns
/** @return NSArray<NSTextStorage *> * */
/** @return NSArray<NSTextStorage *> * */
/** @return NSArray<NSTextStorage *> * */
/** @return NSArray<NSTextStorage *> * */