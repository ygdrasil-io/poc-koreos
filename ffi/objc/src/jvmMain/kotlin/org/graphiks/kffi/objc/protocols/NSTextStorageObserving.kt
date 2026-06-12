package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextStorageObserving
 * Inherits protocols: NSObject
 */
interface NSTextStorageObserving : NSObject {
    fun processEditingForTextStorage_edited_range_changeInLength_invalidatedRange(textStorage: MemorySegment, editMask: NSTextStorageEditActions, newCharRange: NSRange, delta: NSInteger, invalidatedCharRange: NSRange)
    
    fun performEditingTransactionForTextStorage_usingBlock(textStorage: MemorySegment, transaction: MemorySegment)
    
    fun textStorage(): MemorySegment
    
    fun setTextStorage(textStorage: MemorySegment)
    
    // @property textStorage
    fun textStorage(): MemorySegment
    fun setTextStorage(value: MemorySegment)
    
}

