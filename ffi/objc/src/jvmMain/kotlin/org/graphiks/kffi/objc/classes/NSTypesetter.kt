package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTypesetter
 * Superclass: NSObject
 */
open class NSTypesetter(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTypesetter") }
        
        open fun printingAdjustmentInLayoutManager_forNominallySpacedGlyphRange_packedGlyphs_count(layoutMgr: MemorySegment, nominallySpacedGlyphsRange: NSRange, packedGlyphs: MemorySegment, packedGlyphsCount: NSUInteger): NSSize {
            val sel = ObjCRuntime.sel("printingAdjustmentInLayoutManager:forNominallySpacedGlyphRange:packedGlyphs:count:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), _class, sel, layoutMgr, ObjCRuntime.ObjCStructArg(nominallySpacedGlyphsRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), packedGlyphs, packedGlyphsCount) as NSSize
        }
        
        open fun sharedSystemTypesetterForBehavior(behavior: NSTypesetterBehavior): MemorySegment {
            val sel = ObjCRuntime.sel("sharedSystemTypesetterForBehavior:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, behavior) as MemorySegment
        }
        
        open fun sharedSystemTypesetter(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedSystemTypesetter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun defaultTypesetterBehavior(): NSTypesetterBehavior {
            val sel = ObjCRuntime.sel("defaultTypesetterBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSTypesetterBehavior
        }
        
    }
    
    open fun substituteFontForFont(originalFont: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("substituteFontForFont:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, originalFont) as MemorySegment
    }
    
    open fun textTabForGlyphLocation_writingDirection_maxLocation(glyphLocation: CGFloat, direction: NSWritingDirection, maxLocation: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("textTabForGlyphLocation:writingDirection:maxLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphLocation, direction, maxLocation) as MemorySegment
    }
    
    open fun setParagraphGlyphRange_separatorGlyphRange(paragraphRange: NSRange, paragraphSeparatorRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setParagraphGlyphRange:separatorGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(paragraphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(paragraphSeparatorRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun layoutParagraphAtPoint(lineFragmentOrigin: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("layoutParagraphAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, lineFragmentOrigin) as NSUInteger
    }
    
    open fun beginParagraph(): Unit {
        val sel = ObjCRuntime.sel("beginParagraph")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun endParagraph(): Unit {
        val sel = ObjCRuntime.sel("endParagraph")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun beginLineWithGlyphAtIndex(glyphIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("beginLineWithGlyphAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphIndex)
    }
    
    open fun endLineWithGlyphRange(lineGlyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("endLineWithGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(lineGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun lineSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
        val sel = ObjCRuntime.sel("lineSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as CGFloat
    }
    
    open fun paragraphSpacingBeforeGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacingBeforeGlyphAtIndex:withProposedLineFragmentRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as CGFloat
    }
    
    open fun paragraphSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as CGFloat
    }
    
    open fun getLineFragmentRect_usedRect_forParagraphSeparatorGlyphRange_atProposedOrigin(lineFragmentRect: MemorySegment, lineFragmentUsedRect: MemorySegment, paragraphSeparatorGlyphRange: NSRange, lineOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("getLineFragmentRect:usedRect:forParagraphSeparatorGlyphRange:atProposedOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, lineFragmentRect, lineFragmentUsedRect, ObjCRuntime.ObjCStructArg(paragraphSeparatorGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(lineOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun setHardInvalidation_forGlyphRange(flag: BOOL, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setHardInvalidation:forGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun layoutGlyphsInLayoutManager_startingAtGlyphIndex_maxNumberOfLineFragments_nextGlyphIndex(layoutManager: MemorySegment, startGlyphIndex: NSUInteger, maxNumLines: NSUInteger, nextGlyph: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("layoutGlyphsInLayoutManager:startingAtGlyphIndex:maxNumberOfLineFragments:nextGlyphIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, layoutManager, startGlyphIndex, maxNumLines, nextGlyph)
    }
    
    open fun layoutCharactersInRange_forLayoutManager_maximumNumberOfLineFragments(characterRange: NSRange, layoutManager: MemorySegment, maxNumLines: NSUInteger): NSRange {
        val sel = ObjCRuntime.sel("layoutCharactersInRange:forLayoutManager:maximumNumberOfLineFragments:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(characterRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), layoutManager, maxNumLines) as NSRange
    }
    
    open fun baselineOffsetInLayoutManager_glyphIndex(layoutMgr: MemorySegment, glyphIndex: NSUInteger): CGFloat {
        val sel = ObjCRuntime.sel("baselineOffsetInLayoutManager:glyphIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, layoutMgr, glyphIndex) as CGFloat
    }
    
    // @property usesFontLeading
    open fun usesFontLeading(): BOOL {
        val sel = ObjCRuntime.sel("usesFontLeading")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setUsesFontLeading(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesFontLeading:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property typesetterBehavior
    open fun typesetterBehavior(): NSTypesetterBehavior {
        val sel = ObjCRuntime.sel("typesetterBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTypesetterBehavior
    }
    open fun setTypesetterBehavior(value: NSTypesetterBehavior) {
        val sel = ObjCRuntime.sel("setTypesetterBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hyphenationFactor
    open fun hyphenationFactor(): Float {
        val sel = ObjCRuntime.sel("hyphenationFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setHyphenationFactor(value: Float) {
        val sel = ObjCRuntime.sel("setHyphenationFactor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineFragmentPadding
    open fun lineFragmentPadding(): CGFloat {
        val sel = ObjCRuntime.sel("lineFragmentPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setLineFragmentPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineFragmentPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bidiProcessingEnabled
    open fun bidiProcessingEnabled(): BOOL {
        val sel = ObjCRuntime.sel("bidiProcessingEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setBidiProcessingEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setBidiProcessingEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attributedString
    open fun attributedString(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAttributedString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paragraphGlyphRange
    open fun paragraphGlyphRange(): NSRange {
        val sel = ObjCRuntime.sel("paragraphGlyphRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property paragraphSeparatorGlyphRange
    open fun paragraphSeparatorGlyphRange(): NSRange {
        val sel = ObjCRuntime.sel("paragraphSeparatorGlyphRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property paragraphCharacterRange
    open fun paragraphCharacterRange(): NSRange {
        val sel = ObjCRuntime.sel("paragraphCharacterRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property paragraphSeparatorCharacterRange
    open fun paragraphSeparatorCharacterRange(): NSRange {
        val sel = ObjCRuntime.sel("paragraphSeparatorCharacterRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property attributesForExtraLineFragment
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    open fun attributesForExtraLineFragment(): MemorySegment {
        val sel = ObjCRuntime.sel("attributesForExtraLineFragment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property layoutManager
    open fun layoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textContainers
    /** @return NSArray<NSTextContainer *> * */
    open fun textContainers(): MemorySegment {
        val sel = ObjCRuntime.sel("textContainers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentTextContainer
    open fun currentTextContainer(): MemorySegment {
        val sel = ObjCRuntime.sel("currentTextContainer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentParagraphStyle
    open fun currentParagraphStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("currentParagraphStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sharedSystemTypesetter
    open fun sharedSystemTypesetter(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedSystemTypesetter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaultTypesetterBehavior
    open fun defaultTypesetterBehavior(): NSTypesetterBehavior {
        val sel = ObjCRuntime.sel("defaultTypesetterBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTypesetterBehavior
    }
    
}

// ── Category: NSLayoutPhaseInterface on NSTypesetter ─────────────────────────────────────────

fun NSTypesetter.willSetLineFragmentRect_forGlyphRange_usedRect_baselineOffset(lineRect: MemorySegment, glyphRange: NSRange, usedRect: MemorySegment, baselineOffset: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("willSetLineFragmentRect:forGlyphRange:usedRect:baselineOffset:")
    ObjCRuntime.msgSend(null, ptr, sel, lineRect, glyphRange, usedRect, baselineOffset)
}

fun NSTypesetter.shouldBreakLineByWordBeforeCharacterAtIndex(charIndex: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("shouldBreakLineByWordBeforeCharacterAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, charIndex) as BOOL
}

fun NSTypesetter.shouldBreakLineByHyphenatingBeforeCharacterAtIndex(charIndex: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("shouldBreakLineByHyphenatingBeforeCharacterAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, charIndex) as BOOL
}

fun NSTypesetter.hyphenationFactorForGlyphAtIndex(glyphIndex: NSUInteger): Float {
    val sel = ObjCRuntime.sel("hyphenationFactorForGlyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, glyphIndex) as Float
}

fun NSTypesetter.hyphenCharacterForGlyphAtIndex(glyphIndex: NSUInteger): UTF32Char {
    val sel = ObjCRuntime.sel("hyphenCharacterForGlyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, glyphIndex) as UTF32Char
}

fun NSTypesetter.boundingBoxForControlGlyphAtIndex_forTextContainer_proposedLineFragment_glyphPosition_characterIndex(glyphIndex: NSUInteger, textContainer: MemorySegment, proposedRect: NSRect, glyphPosition: NSPoint, charIndex: NSUInteger): NSRect {
    val sel = ObjCRuntime.sel("boundingBoxForControlGlyphAtIndex:forTextContainer:proposedLineFragment:glyphPosition:characterIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, textContainer, proposedRect, glyphPosition, charIndex) as NSRect
}

// ── Category: NSGlyphStorageInterface on NSTypesetter ─────────────────────────────────────────

fun NSTypesetter.characterRangeForGlyphRange_actualGlyphRange(glyphRange: NSRange, actualGlyphRange: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("characterRangeForGlyphRange:actualGlyphRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, glyphRange, actualGlyphRange) as NSRange
}

fun NSTypesetter.glyphRangeForCharacterRange_actualCharacterRange(charRange: NSRange, actualCharRange: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("glyphRangeForCharacterRange:actualCharacterRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, charRange, actualCharRange) as NSRange
}

fun NSTypesetter.getLineFragmentRect_usedRect_remainingRect_forStartingGlyphAtIndex_proposedRect_lineSpacing_paragraphSpacingBefore_paragraphSpacingAfter(lineFragmentRect: MemorySegment, lineFragmentUsedRect: MemorySegment, remainingRect: MemorySegment, startingGlyphIndex: NSUInteger, proposedRect: NSRect, lineSpacing: CGFloat, paragraphSpacingBefore: CGFloat, paragraphSpacingAfter: CGFloat): Unit {
    val sel = ObjCRuntime.sel("getLineFragmentRect:usedRect:remainingRect:forStartingGlyphAtIndex:proposedRect:lineSpacing:paragraphSpacingBefore:paragraphSpacingAfter:")
    ObjCRuntime.msgSend(null, ptr, sel, lineFragmentRect, lineFragmentUsedRect, remainingRect, startingGlyphIndex, proposedRect, lineSpacing, paragraphSpacingBefore, paragraphSpacingAfter)
}

fun NSTypesetter.setLineFragmentRect_forGlyphRange_usedRect_baselineOffset(fragmentRect: NSRect, glyphRange: NSRange, usedRect: NSRect, baselineOffset: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setLineFragmentRect:forGlyphRange:usedRect:baselineOffset:")
    ObjCRuntime.msgSend(null, ptr, sel, fragmentRect, glyphRange, usedRect, baselineOffset)
}

fun NSTypesetter.setNotShownAttribute_forGlyphRange(flag: BOOL, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setNotShownAttribute:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, flag, glyphRange)
}

fun NSTypesetter.setDrawsOutsideLineFragment_forGlyphRange(flag: BOOL, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setDrawsOutsideLineFragment:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, flag, glyphRange)
}

fun NSTypesetter.setLocation_withAdvancements_forStartOfGlyphRange(location: NSPoint, advancements: MemorySegment, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setLocation:withAdvancements:forStartOfGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, location, advancements, glyphRange)
}

fun NSTypesetter.setAttachmentSize_forGlyphRange(attachmentSize: NSSize, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setAttachmentSize:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, attachmentSize, glyphRange)
}

fun NSTypesetter.setBidiLevels_forGlyphRange(levels: MemorySegment, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setBidiLevels:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, levels, glyphRange)
}

// ── Category: NSTypesetter_Deprecated on NSTypesetter ─────────────────────────────────────────

fun NSTypesetter.actionForControlCharacterAtIndex(charIndex: NSUInteger): NSTypesetterControlCharacterAction {
    val sel = ObjCRuntime.sel("actionForControlCharacterAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex) as NSTypesetterControlCharacterAction
}

fun NSTypesetter.getGlyphsInRange_glyphs_characterIndexes_glyphInscriptions_elasticBits_bidiLevels(glyphsRange: NSRange, glyphBuffer: MemorySegment, charIndexBuffer: MemorySegment, inscribeBuffer: MemorySegment, elasticBuffer: MemorySegment, bidiLevelBuffer: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("getGlyphsInRange:glyphs:characterIndexes:glyphInscriptions:elasticBits:bidiLevels:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphsRange, glyphBuffer, charIndexBuffer, inscribeBuffer, elasticBuffer, bidiLevelBuffer) as NSUInteger
}

fun NSTypesetter.substituteGlyphsInRange_withGlyphs(glyphRange: NSRange, glyphs: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("substituteGlyphsInRange:withGlyphs:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphRange, glyphs)
}

fun NSTypesetter.insertGlyph_atGlyphIndex_characterIndex(glyph: NSGlyph, glyphIndex: NSUInteger, characterIndex: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("insertGlyph:atGlyphIndex:characterIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, glyph, glyphIndex, characterIndex)
}

fun NSTypesetter.deleteGlyphsInRange(glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("deleteGlyphsInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphRange)
}

