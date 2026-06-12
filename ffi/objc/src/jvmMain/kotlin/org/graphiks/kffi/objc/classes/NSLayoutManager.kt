package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutManager
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSLayoutManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutManager") }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun replaceTextStorage(newTextStorage: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceTextStorage:")
        ObjCRuntime.msgSend(null, ptr, sel, newTextStorage)
    }
    
    open fun addTextContainer(container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, container)
    }
    
    open fun insertTextContainer_atIndex(container: MemorySegment, index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("insertTextContainer:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, container, index)
    }
    
    open fun removeTextContainerAtIndex(index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("removeTextContainerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    open fun textContainerChangedGeometry(container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textContainerChangedGeometry:")
        ObjCRuntime.msgSend(null, ptr, sel, container)
    }
    
    open fun textContainerChangedTextView(container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textContainerChangedTextView:")
        ObjCRuntime.msgSend(null, ptr, sel, container)
    }
    
    open fun invalidateGlyphsForCharacterRange_changeInLength_actualCharacterRange(charRange: NSRange, delta: NSInteger, actualCharRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateGlyphsForCharacterRange:changeInLength:actualCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), delta, actualCharRange)
    }
    
    open fun invalidateLayoutForCharacterRange_actualCharacterRange(charRange: NSRange, actualCharRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateLayoutForCharacterRange:actualCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), actualCharRange)
    }
    
    open fun invalidateDisplayForCharacterRange(charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("invalidateDisplayForCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun invalidateDisplayForGlyphRange(glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("invalidateDisplayForGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun processEditingForTextStorage_edited_range_changeInLength_invalidatedRange(textStorage: MemorySegment, editMask: NSTextStorageEditActions, newCharRange: NSRange, delta: NSInteger, invalidatedCharRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("processEditingForTextStorage:edited:range:changeInLength:invalidatedRange:")
        ObjCRuntime.msgSend(null, ptr, sel, textStorage, editMask, ObjCRuntime.ObjCStructArg(newCharRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), delta, ObjCRuntime.ObjCStructArg(invalidatedCharRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun ensureGlyphsForCharacterRange(charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("ensureGlyphsForCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun ensureGlyphsForGlyphRange(glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("ensureGlyphsForGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun ensureLayoutForCharacterRange(charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun ensureLayoutForGlyphRange(glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun ensureLayoutForTextContainer(container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, container)
    }
    
    open fun ensureLayoutForBoundingRect_inTextContainer(bounds: NSRect, container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForBoundingRect:inTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(bounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), container)
    }
    
    open fun setGlyphs_properties_characterIndexes_font_forGlyphRange(glyphs: MemorySegment, props: MemorySegment, charIndexes: MemorySegment, aFont: MemorySegment, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setGlyphs:properties:characterIndexes:font:forGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphs, props, charIndexes, aFont, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun CGGlyphAtIndex_isValidIndex(glyphIndex: NSUInteger, isValidIndex: MemorySegment): CGGlyph {
        val sel = ObjCRuntime.sel("CGGlyphAtIndex:isValidIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel, glyphIndex, isValidIndex) as CGGlyph
    }
    
    open fun CGGlyphAtIndex(glyphIndex: NSUInteger): CGGlyph {
        val sel = ObjCRuntime.sel("CGGlyphAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel, glyphIndex) as CGGlyph
    }
    
    open fun isValidGlyphIndex(glyphIndex: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("isValidGlyphIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, glyphIndex) as BOOL
    }
    
    open fun propertyForGlyphAtIndex(glyphIndex: NSUInteger): NSGlyphProperty {
        val sel = ObjCRuntime.sel("propertyForGlyphAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphIndex) as NSGlyphProperty
    }
    
    open fun characterIndexForGlyphAtIndex(glyphIndex: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("characterIndexForGlyphAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphIndex) as NSUInteger
    }
    
    open fun glyphIndexForCharacterAtIndex(charIndex: NSUInteger): NSUInteger {
        val sel = ObjCRuntime.sel("glyphIndexForCharacterAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, charIndex) as NSUInteger
    }
    
    open fun getGlyphsInRange_glyphs_properties_characterIndexes_bidiLevels(glyphRange: NSRange, glyphBuffer: MemorySegment, props: MemorySegment, charIndexBuffer: MemorySegment, bidiLevelBuffer: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("getGlyphsInRange:glyphs:properties:characterIndexes:bidiLevels:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), glyphBuffer, props, charIndexBuffer, bidiLevelBuffer) as NSUInteger
    }
    
    open fun setTextContainer_forGlyphRange(container: MemorySegment, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setTextContainer:forGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, container, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun setLineFragmentRect_forGlyphRange_usedRect(fragmentRect: NSRect, glyphRange: NSRange, usedRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("setLineFragmentRect:forGlyphRange:usedRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(fragmentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(usedRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun setExtraLineFragmentRect_usedRect_textContainer(fragmentRect: NSRect, usedRect: NSRect, container: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setExtraLineFragmentRect:usedRect:textContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(fragmentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(usedRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), container)
    }
    
    open fun setLocation_forStartOfGlyphRange(location: NSPoint, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setLocation:forStartOfGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun setNotShownAttribute_forGlyphAtIndex(flag: BOOL, glyphIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("setNotShownAttribute:forGlyphAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, glyphIndex)
    }
    
    open fun setDrawsOutsideLineFragment_forGlyphAtIndex(flag: BOOL, glyphIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("setDrawsOutsideLineFragment:forGlyphAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, glyphIndex)
    }
    
    open fun setAttachmentSize_forGlyphRange(attachmentSize: NSSize, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setAttachmentSize:forGlyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(attachmentSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun getFirstUnlaidCharacterIndex_glyphIndex(charIndex: MemorySegment, glyphIndex: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getFirstUnlaidCharacterIndex:glyphIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, charIndex, glyphIndex)
    }
    
    open fun firstUnlaidCharacterIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("firstUnlaidCharacterIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    open fun firstUnlaidGlyphIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("firstUnlaidGlyphIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    open fun textContainerForGlyphAtIndex_effectiveRange(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textContainerForGlyphAtIndex:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphIndex, effectiveGlyphRange) as MemorySegment
    }
    
    open fun textContainerForGlyphAtIndex_effectiveRange_withoutAdditionalLayout(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment, flag: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("textContainerForGlyphAtIndex:effectiveRange:withoutAdditionalLayout:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphIndex, effectiveGlyphRange, flag) as MemorySegment
    }
    
    open fun usedRectForTextContainer(container: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("usedRectForTextContainer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, container) as NSRect
    }
    
    open fun lineFragmentRectForGlyphAtIndex_effectiveRange(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("lineFragmentRectForGlyphAtIndex:effectiveRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, effectiveGlyphRange) as NSRect
    }
    
    open fun lineFragmentRectForGlyphAtIndex_effectiveRange_withoutAdditionalLayout(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment, flag: BOOL): NSRect {
        val sel = ObjCRuntime.sel("lineFragmentRectForGlyphAtIndex:effectiveRange:withoutAdditionalLayout:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, effectiveGlyphRange, flag) as NSRect
    }
    
    open fun lineFragmentUsedRectForGlyphAtIndex_effectiveRange(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("lineFragmentUsedRectForGlyphAtIndex:effectiveRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, effectiveGlyphRange) as NSRect
    }
    
    open fun lineFragmentUsedRectForGlyphAtIndex_effectiveRange_withoutAdditionalLayout(glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment, flag: BOOL): NSRect {
        val sel = ObjCRuntime.sel("lineFragmentUsedRectForGlyphAtIndex:effectiveRange:withoutAdditionalLayout:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyphIndex, effectiveGlyphRange, flag) as NSRect
    }
    
    open fun locationForGlyphAtIndex(glyphIndex: NSUInteger): NSPoint {
        val sel = ObjCRuntime.sel("locationForGlyphAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, glyphIndex) as NSPoint
    }
    
    open fun notShownAttributeForGlyphAtIndex(glyphIndex: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("notShownAttributeForGlyphAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, glyphIndex) as BOOL
    }
    
    open fun drawsOutsideLineFragmentForGlyphAtIndex(glyphIndex: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("drawsOutsideLineFragmentForGlyphAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, glyphIndex) as BOOL
    }
    
    open fun attachmentSizeForGlyphAtIndex(glyphIndex: NSUInteger): NSSize {
        val sel = ObjCRuntime.sel("attachmentSizeForGlyphAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, glyphIndex) as NSSize
    }
    
    open fun truncatedGlyphRangeInLineFragmentForGlyphAtIndex(glyphIndex: NSUInteger): NSRange {
        val sel = ObjCRuntime.sel("truncatedGlyphRangeInLineFragmentForGlyphAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, glyphIndex) as NSRange
    }
    
    open fun glyphRangeForCharacterRange_actualCharacterRange(charRange: NSRange, actualCharRange: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("glyphRangeForCharacterRange:actualCharacterRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), actualCharRange) as NSRange
    }
    
    open fun characterRangeForGlyphRange_actualGlyphRange(glyphRange: NSRange, actualGlyphRange: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("characterRangeForGlyphRange:actualGlyphRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), actualGlyphRange) as NSRange
    }
    
    open fun glyphRangeForTextContainer(container: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("glyphRangeForTextContainer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, container) as NSRange
    }
    
    open fun rangeOfNominallySpacedGlyphsContainingIndex(glyphIndex: NSUInteger): NSRange {
        val sel = ObjCRuntime.sel("rangeOfNominallySpacedGlyphsContainingIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, glyphIndex) as NSRange
    }
    
    open fun boundingRectForGlyphRange_inTextContainer(glyphRange: NSRange, container: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("boundingRectForGlyphRange:inTextContainer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), container) as NSRect
    }
    
    open fun glyphRangeForBoundingRect_inTextContainer(bounds: NSRect, container: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("glyphRangeForBoundingRect:inTextContainer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(bounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), container) as NSRange
    }
    
    open fun glyphRangeForBoundingRectWithoutAdditionalLayout_inTextContainer(bounds: NSRect, container: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("glyphRangeForBoundingRectWithoutAdditionalLayout:inTextContainer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(bounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), container) as NSRange
    }
    
    open fun glyphIndexForPoint_inTextContainer_fractionOfDistanceThroughGlyph(point: NSPoint, container: MemorySegment, partialFraction: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("glyphIndexForPoint:inTextContainer:fractionOfDistanceThroughGlyph:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), container, partialFraction) as NSUInteger
    }
    
    open fun glyphIndexForPoint_inTextContainer(point: NSPoint, container: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("glyphIndexForPoint:inTextContainer:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), container) as NSUInteger
    }
    
    open fun fractionOfDistanceThroughGlyphForPoint_inTextContainer(point: NSPoint, container: MemorySegment): CGFloat {
        val sel = ObjCRuntime.sel("fractionOfDistanceThroughGlyphForPoint:inTextContainer:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), container) as CGFloat
    }
    
    open fun characterIndexForPoint_inTextContainer_fractionOfDistanceBetweenInsertionPoints(point: NSPoint, container: MemorySegment, partialFraction: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("characterIndexForPoint:inTextContainer:fractionOfDistanceBetweenInsertionPoints:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), container, partialFraction) as NSUInteger
    }
    
    open fun getLineFragmentInsertionPointsForCharacterAtIndex_alternatePositions_inDisplayOrder_positions_characterIndexes(charIndex: NSUInteger, aFlag: BOOL, dFlag: BOOL, positions: MemorySegment, charIndexes: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("getLineFragmentInsertionPointsForCharacterAtIndex:alternatePositions:inDisplayOrder:positions:characterIndexes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, charIndex, aFlag, dFlag, positions, charIndexes) as NSUInteger
    }
    
    open fun enumerateLineFragmentsForGlyphRange_usingBlock(glyphRange: NSRange, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateLineFragmentsForGlyphRange:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), block)
    }
    
    open fun enumerateEnclosingRectsForGlyphRange_withinSelectedGlyphRange_inTextContainer_usingBlock(glyphRange: NSRange, selectedRange: NSRange, textContainer: MemorySegment, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateEnclosingRectsForGlyphRange:withinSelectedGlyphRange:inTextContainer:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(selectedRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), textContainer, block)
    }
    
    open fun drawBackgroundForGlyphRange_atPoint(glyphsToShow: NSRange, origin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawBackgroundForGlyphRange:atPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphsToShow, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(origin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun drawGlyphsForGlyphRange_atPoint(glyphsToShow: NSRange, origin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawGlyphsForGlyphRange:atPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphsToShow, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(origin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun showCGGlyphs_positions_count_font_textMatrix_attributes_inContext(glyphs: MemorySegment, positions: MemorySegment, glyphCount: NSInteger, font: MemorySegment, textMatrix: CGAffineTransform, attributes: MemorySegment, CGContext: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showCGGlyphs:positions:count:font:textMatrix:attributes:inContext:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphs, positions, glyphCount, font, textMatrix, attributes, CGContext)
    }
    
    open fun fillBackgroundRectArray_count_forCharacterRange_color(rectArray: MemorySegment, rectCount: NSUInteger, charRange: NSRange, color: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("fillBackgroundRectArray:count:forCharacterRange:color:")
        ObjCRuntime.msgSend(null, ptr, sel, rectArray, rectCount, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), color)
    }
    
    open fun drawUnderlineForGlyphRange_underlineType_baselineOffset_lineFragmentRect_lineFragmentGlyphRange_containerOrigin(glyphRange: NSRange, underlineVal: NSUnderlineStyle, baselineOffset: CGFloat, lineRect: NSRect, lineGlyphRange: NSRange, containerOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawUnderlineForGlyphRange:underlineType:baselineOffset:lineFragmentRect:lineFragmentGlyphRange:containerOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), underlineVal, baselineOffset, ObjCRuntime.ObjCStructArg(lineRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(lineGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(containerOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun underlineGlyphRange_underlineType_lineFragmentRect_lineFragmentGlyphRange_containerOrigin(glyphRange: NSRange, underlineVal: NSUnderlineStyle, lineRect: NSRect, lineGlyphRange: NSRange, containerOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("underlineGlyphRange:underlineType:lineFragmentRect:lineFragmentGlyphRange:containerOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), underlineVal, ObjCRuntime.ObjCStructArg(lineRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(lineGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(containerOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun drawStrikethroughForGlyphRange_strikethroughType_baselineOffset_lineFragmentRect_lineFragmentGlyphRange_containerOrigin(glyphRange: NSRange, strikethroughVal: NSUnderlineStyle, baselineOffset: CGFloat, lineRect: NSRect, lineGlyphRange: NSRange, containerOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawStrikethroughForGlyphRange:strikethroughType:baselineOffset:lineFragmentRect:lineFragmentGlyphRange:containerOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), strikethroughVal, baselineOffset, ObjCRuntime.ObjCStructArg(lineRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(lineGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(containerOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun strikethroughGlyphRange_strikethroughType_lineFragmentRect_lineFragmentGlyphRange_containerOrigin(glyphRange: NSRange, strikethroughVal: NSUnderlineStyle, lineRect: NSRect, lineGlyphRange: NSRange, containerOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("strikethroughGlyphRange:strikethroughType:lineFragmentRect:lineFragmentGlyphRange:containerOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), strikethroughVal, ObjCRuntime.ObjCStructArg(lineRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(lineGlyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), ObjCRuntime.ObjCStructArg(containerOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun showAttachmentCell_inRect_characterIndex(cell: MemorySegment, rect: NSRect, attachmentIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("showAttachmentCell:inRect:characterIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, cell, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), attachmentIndex)
    }
    
    open fun setLayoutRect_forTextBlock_glyphRange(rect: NSRect, block: MemorySegment, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setLayoutRect:forTextBlock:glyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), block, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun setBoundsRect_forTextBlock_glyphRange(rect: NSRect, block: MemorySegment, glyphRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setBoundsRect:forTextBlock:glyphRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), block, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun layoutRectForTextBlock_glyphRange(block: MemorySegment, glyphRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("layoutRectForTextBlock:glyphRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    open fun boundsRectForTextBlock_glyphRange(block: MemorySegment, glyphRange: NSRange): NSRect {
        val sel = ObjCRuntime.sel("boundsRectForTextBlock:glyphRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, ObjCRuntime.ObjCStructArg(glyphRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRect
    }
    
    open fun layoutRectForTextBlock_atIndex_effectiveRange(block: MemorySegment, glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("layoutRectForTextBlock:atIndex:effectiveRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, glyphIndex, effectiveGlyphRange) as NSRect
    }
    
    open fun boundsRectForTextBlock_atIndex_effectiveRange(block: MemorySegment, glyphIndex: NSUInteger, effectiveGlyphRange: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("boundsRectForTextBlock:atIndex:effectiveRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, block, glyphIndex, effectiveGlyphRange) as NSRect
    }
    
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    open fun temporaryAttributesAtCharacterIndex_effectiveRange(charIndex: NSUInteger, effectiveCharRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("temporaryAttributesAtCharacterIndex:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex, effectiveCharRange) as MemorySegment
    }
    
    open fun setTemporaryAttributes_forCharacterRange(attrs: MemorySegment, charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("setTemporaryAttributes:forCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, attrs, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun addTemporaryAttributes_forCharacterRange(attrs: MemorySegment, charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("addTemporaryAttributes:forCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, attrs, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun removeTemporaryAttribute_forCharacterRange(attrName: NSAttributedStringKey, charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("removeTemporaryAttribute:forCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, attrName, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun temporaryAttribute_atCharacterIndex_effectiveRange(attrName: NSAttributedStringKey, location: NSUInteger, range: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("temporaryAttribute:atCharacterIndex:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrName, location, range) as MemorySegment
    }
    
    open fun temporaryAttribute_atCharacterIndex_longestEffectiveRange_inRange(attrName: NSAttributedStringKey, location: NSUInteger, range: MemorySegment, rangeLimit: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("temporaryAttribute:atCharacterIndex:longestEffectiveRange:inRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrName, location, range, ObjCRuntime.ObjCStructArg(rangeLimit, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    open fun temporaryAttributesAtCharacterIndex_longestEffectiveRange_inRange(location: NSUInteger, range: MemorySegment, rangeLimit: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("temporaryAttributesAtCharacterIndex:longestEffectiveRange:inRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, range, ObjCRuntime.ObjCStructArg(rangeLimit, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    open fun addTemporaryAttribute_value_forCharacterRange(attrName: NSAttributedStringKey, value: MemorySegment, charRange: NSRange): Unit {
        val sel = ObjCRuntime.sel("addTemporaryAttribute:value:forCharacterRange:")
        ObjCRuntime.msgSend(null, ptr, sel, attrName, value, ObjCRuntime.ObjCStructArg(charRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun defaultLineHeightForFont(theFont: MemorySegment): CGFloat {
        val sel = ObjCRuntime.sel("defaultLineHeightForFont:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, theFont) as CGFloat
    }
    
    open fun defaultBaselineOffsetForFont(theFont: MemorySegment): CGFloat {
        val sel = ObjCRuntime.sel("defaultBaselineOffsetForFont:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, theFont) as CGFloat
    }
    
    // @property textStorage
    open fun textStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("textStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTextStorage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextStorage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textContainers
    /** @return NSArray<NSTextContainer *> * */
    open fun textContainers(): MemorySegment {
        val sel = ObjCRuntime.sel("textContainers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSLayoutManagerDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsInvisibleCharacters
    open fun showsInvisibleCharacters(): BOOL {
        val sel = ObjCRuntime.sel("showsInvisibleCharacters")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setShowsInvisibleCharacters(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsInvisibleCharacters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsControlCharacters
    open fun showsControlCharacters(): BOOL {
        val sel = ObjCRuntime.sel("showsControlCharacters")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setShowsControlCharacters(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsControlCharacters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesDefaultHyphenation
    open fun usesDefaultHyphenation(): BOOL {
        val sel = ObjCRuntime.sel("usesDefaultHyphenation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setUsesDefaultHyphenation(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesDefaultHyphenation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property allowsNonContiguousLayout
    open fun allowsNonContiguousLayout(): BOOL {
        val sel = ObjCRuntime.sel("allowsNonContiguousLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsNonContiguousLayout(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsNonContiguousLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasNonContiguousLayout
    open fun hasNonContiguousLayout(): BOOL {
        val sel = ObjCRuntime.sel("hasNonContiguousLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property limitsLayoutForSuspiciousContents
    open fun limitsLayoutForSuspiciousContents(): BOOL {
        val sel = ObjCRuntime.sel("limitsLayoutForSuspiciousContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setLimitsLayoutForSuspiciousContents(value: BOOL) {
        val sel = ObjCRuntime.sel("setLimitsLayoutForSuspiciousContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundLayoutEnabled
    open fun backgroundLayoutEnabled(): BOOL {
        val sel = ObjCRuntime.sel("backgroundLayoutEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setBackgroundLayoutEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setBackgroundLayoutEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultAttachmentScaling
    open fun defaultAttachmentScaling(): NSImageScaling {
        val sel = ObjCRuntime.sel("defaultAttachmentScaling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageScaling
    }
    open fun setDefaultAttachmentScaling(value: NSImageScaling) {
        val sel = ObjCRuntime.sel("setDefaultAttachmentScaling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property typesetter
    open fun typesetter(): MemorySegment {
        val sel = ObjCRuntime.sel("typesetter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTypesetter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTypesetter:")
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
    
    // @property numberOfGlyphs
    open fun numberOfGlyphs(): NSUInteger {
        val sel = ObjCRuntime.sel("numberOfGlyphs")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property extraLineFragmentRect
    open fun extraLineFragmentRect(): NSRect {
        val sel = ObjCRuntime.sel("extraLineFragmentRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property extraLineFragmentUsedRect
    open fun extraLineFragmentUsedRect(): NSRect {
        val sel = ObjCRuntime.sel("extraLineFragmentUsedRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property extraLineFragmentTextContainer
    open fun extraLineFragmentTextContainer(): MemorySegment {
        val sel = ObjCRuntime.sel("extraLineFragmentTextContainer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSTextViewSupport on NSLayoutManager ─────────────────────────────────────────

/** @return NSArray<NSRulerMarker *> * */
fun NSLayoutManager.rulerMarkersForTextView_paragraphStyle_ruler(view: MemorySegment, style: MemorySegment, ruler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("rulerMarkersForTextView:paragraphStyle:ruler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, style, ruler) as MemorySegment
}

fun NSLayoutManager.rulerAccessoryViewForTextView_paragraphStyle_ruler_enabled(view: MemorySegment, style: MemorySegment, ruler: MemorySegment, isEnabled: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("rulerAccessoryViewForTextView:paragraphStyle:ruler:enabled:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, style, ruler, isEnabled) as MemorySegment
}

fun NSLayoutManager.layoutManagerOwnsFirstResponderInWindow(window: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("layoutManagerOwnsFirstResponderInWindow:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, window) as BOOL
}

fun NSLayoutManager.firstTextView(): MemorySegment {
    val sel = ObjCRuntime.sel("firstTextView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSLayoutManager.textViewForBeginningOfSelection(): MemorySegment {
    val sel = ObjCRuntime.sel("textViewForBeginningOfSelection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property firstTextView
fun NSLayoutManager.glyphAtIndex_isValidIndex(glyphIndex: NSUInteger, isValidIndex: MemorySegment): NSGlyph {
    val sel = ObjCRuntime.sel("glyphAtIndex:isValidIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, glyphIndex, isValidIndex) as NSGlyph
}

fun NSLayoutManager.glyphAtIndex(glyphIndex: NSUInteger): NSGlyph {
    val sel = ObjCRuntime.sel("glyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, glyphIndex) as NSGlyph
}

fun NSLayoutManager.rectArrayForCharacterRange_withinSelectedCharacterRange_inTextContainer_rectCount(charRange: NSRange, selCharRange: NSRange, container: MemorySegment, rectCount: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("rectArrayForCharacterRange:withinSelectedCharacterRange:inTextContainer:rectCount:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charRange, selCharRange, container, rectCount) as MemorySegment
}

fun NSLayoutManager.rectArrayForGlyphRange_withinSelectedGlyphRange_inTextContainer_rectCount(glyphRange: NSRange, selGlyphRange: NSRange, container: MemorySegment, rectCount: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("rectArrayForGlyphRange:withinSelectedGlyphRange:inTextContainer:rectCount:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, glyphRange, selGlyphRange, container, rectCount) as MemorySegment
}

fun NSLayoutManager.substituteFontForFont(originalFont: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("substituteFontForFont:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, originalFont) as MemorySegment
}

fun NSLayoutManager.insertGlyphs_length_forStartingGlyphAtIndex_characterIndex(glyphs: MemorySegment, length: NSUInteger, glyphIndex: NSUInteger, charIndex: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("insertGlyphs:length:forStartingGlyphAtIndex:characterIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphs, length, glyphIndex, charIndex)
}

fun NSLayoutManager.insertGlyph_atGlyphIndex_characterIndex(glyph: NSGlyph, glyphIndex: NSUInteger, charIndex: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("insertGlyph:atGlyphIndex:characterIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, glyph, glyphIndex, charIndex)
}

fun NSLayoutManager.replaceGlyphAtIndex_withGlyph(glyphIndex: NSUInteger, newGlyph: NSGlyph): Unit {
    val sel = ObjCRuntime.sel("replaceGlyphAtIndex:withGlyph:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphIndex, newGlyph)
}

fun NSLayoutManager.deleteGlyphsInRange(glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("deleteGlyphsInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphRange)
}

fun NSLayoutManager.setCharacterIndex_forGlyphAtIndex(charIndex: NSUInteger, glyphIndex: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setCharacterIndex:forGlyphAtIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, charIndex, glyphIndex)
}

fun NSLayoutManager.setIntAttribute_value_forGlyphAtIndex(attributeTag: NSInteger, `val`: NSInteger, glyphIndex: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setIntAttribute:value:forGlyphAtIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, attributeTag, `val`, glyphIndex)
}

fun NSLayoutManager.invalidateGlyphsOnLayoutInvalidationForGlyphRange(glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("invalidateGlyphsOnLayoutInvalidationForGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphRange)
}

fun NSLayoutManager.intAttribute_forGlyphAtIndex(attributeTag: NSInteger, glyphIndex: NSUInteger): NSInteger {
    val sel = ObjCRuntime.sel("intAttribute:forGlyphAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, attributeTag, glyphIndex) as NSInteger
}

fun NSLayoutManager.getGlyphsInRange_glyphs_characterIndexes_glyphInscriptions_elasticBits(glyphRange: NSRange, glyphBuffer: MemorySegment, charIndexBuffer: MemorySegment, inscribeBuffer: MemorySegment, elasticBuffer: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("getGlyphsInRange:glyphs:characterIndexes:glyphInscriptions:elasticBits:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphRange, glyphBuffer, charIndexBuffer, inscribeBuffer, elasticBuffer) as NSUInteger
}

fun NSLayoutManager.getGlyphsInRange_glyphs_characterIndexes_glyphInscriptions_elasticBits_bidiLevels(glyphRange: NSRange, glyphBuffer: MemorySegment, charIndexBuffer: MemorySegment, inscribeBuffer: MemorySegment, elasticBuffer: MemorySegment, bidiLevelBuffer: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("getGlyphsInRange:glyphs:characterIndexes:glyphInscriptions:elasticBits:bidiLevels:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphRange, glyphBuffer, charIndexBuffer, inscribeBuffer, elasticBuffer, bidiLevelBuffer) as NSUInteger
}

fun NSLayoutManager.getGlyphs_range(glyphArray: MemorySegment, glyphRange: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("getGlyphs:range:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, glyphArray, glyphRange) as NSUInteger
}

fun NSLayoutManager.invalidateLayoutForCharacterRange_isSoft_actualCharacterRange(charRange: NSRange, flag: BOOL, actualCharRange: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("invalidateLayoutForCharacterRange:isSoft:actualCharacterRange:")
    ObjCRuntime.msgSend(null, ptr, sel, charRange, flag, actualCharRange)
}

fun NSLayoutManager.textStorage_edited_range_changeInLength_invalidatedRange(str: MemorySegment, editedMask: NSTextStorageEditedOptions, newCharRange: NSRange, delta: NSInteger, invalidatedCharRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("textStorage:edited:range:changeInLength:invalidatedRange:")
    ObjCRuntime.msgSend(null, ptr, sel, str, editedMask, newCharRange, delta, invalidatedCharRange)
}

fun NSLayoutManager.setLocations_startingGlyphIndexes_count_forGlyphRange(locations: MemorySegment, glyphIndexes: MemorySegment, count: NSUInteger, glyphRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setLocations:startingGlyphIndexes:count:forGlyphRange:")
    ObjCRuntime.msgSend(null, ptr, sel, locations, glyphIndexes, count, glyphRange)
}

fun NSLayoutManager.showPackedGlyphs_length_glyphRange_atPoint_font_color_printingAdjustment(glyphs: MemorySegment, glyphLen: NSUInteger, glyphRange: NSRange, point: NSPoint, font: MemorySegment, color: MemorySegment, printingAdjustment: NSSize): Unit {
    val sel = ObjCRuntime.sel("showPackedGlyphs:length:glyphRange:atPoint:font:color:printingAdjustment:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphs, glyphLen, glyphRange, point, font, color, printingAdjustment)
}

fun NSLayoutManager.showCGGlyphs_positions_count_font_matrix_attributes_inContext(glyphs: MemorySegment, positions: MemorySegment, glyphCount: NSUInteger, font: MemorySegment, textMatrix: MemorySegment, attributes: MemorySegment, graphicsContext: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("showCGGlyphs:positions:count:font:matrix:attributes:inContext:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphs, positions, glyphCount, font, textMatrix, attributes, graphicsContext)
}

fun NSLayoutManager.usesScreenFonts(): BOOL {
    val sel = ObjCRuntime.sel("usesScreenFonts")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSLayoutManager.setUsesScreenFonts(usesScreenFonts: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesScreenFonts:")
    ObjCRuntime.msgSend(null, ptr, sel, usesScreenFonts)
}

fun NSLayoutManager.hyphenationFactor(): Float {
    val sel = ObjCRuntime.sel("hyphenationFactor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

fun NSLayoutManager.setHyphenationFactor(hyphenationFactor: Float): Unit {
    val sel = ObjCRuntime.sel("setHyphenationFactor:")
    ObjCRuntime.msgSend(null, ptr, sel, hyphenationFactor)
}

// @property usesScreenFonts
fun NSLayoutManager.glyphGenerator(): MemorySegment {
    val sel = ObjCRuntime.sel("glyphGenerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSLayoutManager.setGlyphGenerator(glyphGenerator: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setGlyphGenerator:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphGenerator)
}

// @property glyphGenerator