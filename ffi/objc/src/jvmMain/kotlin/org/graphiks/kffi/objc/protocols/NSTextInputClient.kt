package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextInputClient
 */
interface NSTextInputClient {
    fun insertText_replacementRange(string: MemorySegment, replacementRange: NSRange)
    
    fun doCommandBySelector(selector: MemorySegment)
    
    fun setMarkedText_selectedRange_replacementRange(string: MemorySegment, selectedRange: NSRange, replacementRange: NSRange)
    
    fun unmarkText()
    
    fun selectedRange(): NSRange
    
    fun markedRange(): NSRange
    
    fun hasMarkedText(): BOOL
    
    fun attributedSubstringForProposedRange_actualRange(range: NSRange, actualRange: MemorySegment): MemorySegment
    
    /** @return NSArray<NSAttributedStringKey> * */
    fun validAttributesForMarkedText(): MemorySegment
    
    fun firstRectForCharacterRange_actualRange(range: NSRange, actualRange: MemorySegment): NSRect
    
    fun characterIndexForPoint(point: NSPoint): NSUInteger
    
    // @optional
    fun attributedString(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'attributedString' not implemented")
    
    // @optional
    fun fractionOfDistanceThroughGlyphForPoint(point: NSPoint): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'fractionOfDistanceThroughGlyphForPoint:' not implemented")
    
    // @optional
    fun baselineDeltaForCharacterAtIndex(anIndex: NSUInteger): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'baselineDeltaForCharacterAtIndex:' not implemented")
    
    // @optional
    fun windowLevel(): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'windowLevel' not implemented")
    
    // @optional
    fun drawsVerticallyForCharacterAtIndex(charIndex: NSUInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'drawsVerticallyForCharacterAtIndex:' not implemented")
    
    // @optional
    fun preferredTextAccessoryPlacement(): NSTextCursorAccessoryPlacement =
        throw UnsupportedOperationException("Optional ObjC method 'preferredTextAccessoryPlacement' not implemented")
    
    // @optional
    fun insertAdaptiveImageGlyph_replacementRange(adaptiveImageGlyph: MemorySegment, replacementRange: NSRange): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'insertAdaptiveImageGlyph:replacementRange:' not implemented")
    
    // @optional
    fun unionRectInVisibleSelectedRange(): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'unionRectInVisibleSelectedRange' not implemented")
    
    // @optional
    fun documentVisibleRect(): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'documentVisibleRect' not implemented")
    
    // @optional
    fun supportsAdaptiveImageGlyph(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'supportsAdaptiveImageGlyph' not implemented")
    
    // @property unionRectInVisibleSelectedRange
}

