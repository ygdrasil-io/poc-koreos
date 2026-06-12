package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSATSTypesetter
 * Superclass: NSTypesetter
 */
open class NSATSTypesetter(ptr: MemorySegment) : NSTypesetter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSATSTypesetter") }
        
        fun sharedTypesetter(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedTypesetter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property sharedTypesetter
}

// ── Category: NSPantherCompatibility on NSATSTypesetter ─────────────────────────────────────────

fun NSATSTypesetter.lineFragmentRectForProposedRect_remainingRect(proposedRect: NSRect, remainingRect: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("lineFragmentRectForProposedRect:remainingRect:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, proposedRect, remainingRect) as NSRect
}

// ── Category: NSPrimitiveInterface on NSATSTypesetter ─────────────────────────────────────────

fun NSATSTypesetter.substituteFontForFont(originalFont: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("substituteFontForFont:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, originalFont) as MemorySegment
}

fun NSATSTypesetter.textTabForGlyphLocation_writingDirection_maxLocation(glyphLocation: CGFloat, direction: NSWritingDirection, maxLocation: CGFloat): MemorySegment {
    val sel = ObjCRuntime.sel("textTabForGlyphLocation:writingDirection:maxLocation:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphLocation, direction, maxLocation) as MemorySegment
}

fun NSATSTypesetter.setParagraphGlyphRange_separatorGlyphRange(paragraphRange: NSRange, paragraphSeparatorRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setParagraphGlyphRange:separatorGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, paragraphRange, paragraphSeparatorRange)
}

fun NSATSTypesetter.layoutParagraphAtPoint(lineFragmentOrigin: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("layoutParagraphAtPoint:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, lineFragmentOrigin) as NSUInteger
}

fun NSATSTypesetter.lineSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
    val sel = ObjCRuntime.sel("lineSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, rect) as CGFloat
}

fun NSATSTypesetter.paragraphSpacingBeforeGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
    val sel = ObjCRuntime.sel("paragraphSpacingBeforeGlyphAtIndex:withProposedLineFragmentRect:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, rect) as CGFloat
}

fun NSATSTypesetter.paragraphSpacingAfterGlyphAtIndex_withProposedLineFragmentRect(glyphIndex: NSUInteger, rect: NSRect): CGFloat {
    val sel = ObjCRuntime.sel("paragraphSpacingAfterGlyphAtIndex:withProposedLineFragmentRect:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, glyphIndex, rect) as CGFloat
}

fun NSATSTypesetter.setHardInvalidation_forGlyphRange(flag: BOOL, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setHardInvalidation:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, flag, glyphRange)
}

fun NSATSTypesetter.getLineFragmentRect_usedRect_forParagraphSeparatorGlyphRange_atProposedOrigin(lineFragmentRect: MemorySegment, lineFragmentUsedRect: MemorySegment, paragraphSeparatorGlyphRange: NSRange, lineOrigin: NSPoint): Unit {
    val sel = ObjCRuntime.sel("getLineFragmentRect:usedRect:forParagraphSeparatorGlyphRange:atProposedOrigin:")
    ObjCRuntime.msgSend(null, ptr, sel, lineFragmentRect, lineFragmentUsedRect, paragraphSeparatorGlyphRange, lineOrigin)
}

fun NSATSTypesetter.usesFontLeading(): BOOL {
    val sel = ObjCRuntime.sel("usesFontLeading")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSATSTypesetter.setUsesFontLeading(usesFontLeading: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesFontLeading:")
    ObjCRuntime.msgSend(null, ptr, sel, usesFontLeading)
}

fun NSATSTypesetter.typesetterBehavior(): NSTypesetterBehavior {
    val sel = ObjCRuntime.sel("typesetterBehavior")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTypesetterBehavior
}

fun NSATSTypesetter.setTypesetterBehavior(typesetterBehavior: NSTypesetterBehavior): Unit {
    val sel = ObjCRuntime.sel("setTypesetterBehavior:")
    ObjCRuntime.msgSend(null, ptr, sel, typesetterBehavior)
}

fun NSATSTypesetter.hyphenationFactor(): Float {
    val sel = ObjCRuntime.sel("hyphenationFactor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

fun NSATSTypesetter.setHyphenationFactor(hyphenationFactor: Float): Unit {
    val sel = ObjCRuntime.sel("setHyphenationFactor:")
    ObjCRuntime.msgSend(null, ptr, sel, hyphenationFactor)
}

fun NSATSTypesetter.lineFragmentPadding(): CGFloat {
    val sel = ObjCRuntime.sel("lineFragmentPadding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSATSTypesetter.setLineFragmentPadding(lineFragmentPadding: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setLineFragmentPadding:")
    ObjCRuntime.msgSend(null, ptr, sel, lineFragmentPadding)
}

fun NSATSTypesetter.bidiProcessingEnabled(): BOOL {
    val sel = ObjCRuntime.sel("bidiProcessingEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSATSTypesetter.setBidiProcessingEnabled(bidiProcessingEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setBidiProcessingEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, bidiProcessingEnabled)
}

fun NSATSTypesetter.attributedString(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSATSTypesetter.setAttributedString(attributedString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedString:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedString)
}

fun NSATSTypesetter.paragraphGlyphRange(): NSRange {
    val sel = ObjCRuntime.sel("paragraphGlyphRange")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

fun NSATSTypesetter.paragraphSeparatorGlyphRange(): NSRange {
    val sel = ObjCRuntime.sel("paragraphSeparatorGlyphRange")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

fun NSATSTypesetter.layoutManager(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutManager")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSATSTypesetter.currentTextContainer(): MemorySegment {
    val sel = ObjCRuntime.sel("currentTextContainer")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property usesFontLeading
fun NSATSTypesetter.willSetLineFragmentRect_forGlyphRange_usedRect_baselineOffset(lineRect: MemorySegment, glyphRange: NSRange, usedRect: MemorySegment, baselineOffset: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("willSetLineFragmentRect:forGlyphRange:usedRect:baselineOffset:")
    ObjCRuntime.msgSend(null, ptr, sel, lineRect, glyphRange, usedRect, baselineOffset)
}

fun NSATSTypesetter.shouldBreakLineByWordBeforeCharacterAtIndex(charIndex: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("shouldBreakLineByWordBeforeCharacterAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, charIndex) as BOOL
}

fun NSATSTypesetter.shouldBreakLineByHyphenatingBeforeCharacterAtIndex(charIndex: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("shouldBreakLineByHyphenatingBeforeCharacterAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, charIndex) as BOOL
}

fun NSATSTypesetter.hyphenationFactorForGlyphAtIndex(glyphIndex: NSUInteger): Float {
    val sel = ObjCRuntime.sel("hyphenationFactorForGlyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, glyphIndex) as Float
}

fun NSATSTypesetter.hyphenCharacterForGlyphAtIndex(glyphIndex: NSUInteger): UTF32Char {
    val sel = ObjCRuntime.sel("hyphenCharacterForGlyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, glyphIndex) as UTF32Char
}

fun NSATSTypesetter.boundingBoxForControlGlyphAtIndex_forTextContainer_proposedLineFragment_glyphPosition_characterIndex(glyphIndex: NSUInteger, textContainer: MemorySegment, proposedRect: NSRect, glyphPosition: NSPoint, charIndex: NSUInteger): NSRect {
    val sel = ObjCRuntime.sel("boundingBoxForControlGlyphAtIndex:forTextContainer:proposedLineFragment:glyphPosition:characterIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, textContainer, proposedRect, glyphPosition, charIndex) as NSRect
}

// ── Category: NSGlyphStorageInterface on NSATSTypesetter ─────────────────────────────────────────

fun NSATSTypesetter.getGlyphsInRange_glyphs_characterIndexes_glyphInscriptions_elasticBits(glyphsRange: NSRange, glyphBuffer: MemorySegment, charIndexBuffer: MemorySegment, inscribeBuffer: MemorySegment, elasticBuffer: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("getGlyphsInRange:glyphs:characterIndexes:glyphInscriptions:elasticBits:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphsRange, glyphBuffer, charIndexBuffer, inscribeBuffer, elasticBuffer) as NSUInteger
}

