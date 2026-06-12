package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSLayoutManagerDelegate
 * Inherits protocols: NSObject
 */
interface NSLayoutManagerDelegate : NSObject {
    // @optional
    fun layoutManager_shouldGenerateGlyphs_properties_characterIndexes_font_forGlyphRange(layoutManager: MemorySegment, glyphs: MemorySegment, props: MemorySegment, charIndexes: MemorySegment, aFont: MemorySegment, glyphRange: NSRange): NSUInteger =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldGenerateGlyphs:properties:characterIndexes:font:forGlyphRange:' not implemented")
    
    // @optional
    fun layoutManager_lineSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(layoutManager: MemorySegment, glyphIndex: NSUInteger, rect: NSRect): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:lineSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:' not implemented")
    
    // @optional
    fun layoutManager_paragraphSpacingBeforeGlyphAtIndex_withProposedLineFragmentRect(layoutManager: MemorySegment, glyphIndex: NSUInteger, rect: NSRect): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:paragraphSpacingBeforeGlyphAtIndex:withProposedLineFragmentRect:' not implemented")
    
    // @optional
    fun layoutManager_paragraphSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(layoutManager: MemorySegment, glyphIndex: NSUInteger, rect: NSRect): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:paragraphSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:' not implemented")
    
    // @optional
    fun layoutManager_shouldUseAction_forControlCharacterAtIndex(layoutManager: MemorySegment, action: NSControlCharacterAction, charIndex: NSUInteger): NSControlCharacterAction =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldUseAction:forControlCharacterAtIndex:' not implemented")
    
    // @optional
    fun layoutManager_shouldBreakLineByWordBeforeCharacterAtIndex(layoutManager: MemorySegment, charIndex: NSUInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldBreakLineByWordBeforeCharacterAtIndex:' not implemented")
    
    // @optional
    fun layoutManager_shouldBreakLineByHyphenatingBeforeCharacterAtIndex(layoutManager: MemorySegment, charIndex: NSUInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldBreakLineByHyphenatingBeforeCharacterAtIndex:' not implemented")
    
    // @optional
    fun layoutManager_boundingBoxForControlGlyphAtIndex_forTextContainer_proposedLineFragment_glyphPosition_characterIndex(layoutManager: MemorySegment, glyphIndex: NSUInteger, textContainer: MemorySegment, proposedRect: NSRect, glyphPosition: NSPoint, charIndex: NSUInteger): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:boundingBoxForControlGlyphAtIndex:forTextContainer:proposedLineFragment:glyphPosition:characterIndex:' not implemented")
    
    // @optional
    fun layoutManager_shouldSetLineFragmentRect_lineFragmentUsedRect_baselineOffset_inTextContainer_forGlyphRange(layoutManager: MemorySegment, lineFragmentRect: MemorySegment, lineFragmentUsedRect: MemorySegment, baselineOffset: MemorySegment, textContainer: MemorySegment, glyphRange: NSRange): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldSetLineFragmentRect:lineFragmentUsedRect:baselineOffset:inTextContainer:forGlyphRange:' not implemented")
    
    // @optional
    fun layoutManagerDidInvalidateLayout(sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManagerDidInvalidateLayout:' not implemented")
    
    // @optional
    fun layoutManager_didCompleteLayoutForTextContainer_atEnd(layoutManager: MemorySegment, textContainer: MemorySegment, layoutFinishedFlag: BOOL): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:didCompleteLayoutForTextContainer:atEnd:' not implemented")
    
    // @optional
    fun layoutManager_textContainer_didChangeGeometryFromSize(layoutManager: MemorySegment, textContainer: MemorySegment, oldSize: NSSize): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:textContainer:didChangeGeometryFromSize:' not implemented")
    
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    // @optional
    fun layoutManager_shouldUseTemporaryAttributes_forDrawingToScreen_atCharacterIndex_effectiveRange(layoutManager: MemorySegment, attrs: MemorySegment, toScreen: BOOL, charIndex: NSUInteger, effectiveCharRange: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'layoutManager:shouldUseTemporaryAttributes:forDrawingToScreen:atCharacterIndex:effectiveRange:' not implemented")
    
}

