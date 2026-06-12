package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextInput
 */
interface NSTextInput {
    fun insertText(string: MemorySegment)
    
    fun doCommandBySelector(selector: MemorySegment)
    
    fun setMarkedText_selectedRange(string: MemorySegment, selRange: NSRange)
    
    fun unmarkText()
    
    fun hasMarkedText(): BOOL
    
    fun conversationIdentifier(): NSInteger
    
    fun attributedSubstringFromRange(range: NSRange): MemorySegment
    
    fun markedRange(): NSRange
    
    fun selectedRange(): NSRange
    
    fun firstRectForCharacterRange(range: NSRange): NSRect
    
    fun characterIndexForPoint(point: NSPoint): NSUInteger
    
    fun validAttributesForMarkedText(): MemorySegment
    
}

