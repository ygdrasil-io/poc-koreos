package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableParagraphStyle
 * Superclass: NSParagraphStyle
 */
open class NSMutableParagraphStyle(ptr: MemorySegment) : NSParagraphStyle(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableParagraphStyle") }
        
    }
    
    fun addTabStop(anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTabStop:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject)
    }
    
    fun removeTabStop(anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeTabStop:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject)
    }
    
    fun setParagraphStyle(obj: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setParagraphStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, obj)
    }
    
    // @property lineSpacing
    override fun `lineSpacing`(): CGFloat {
        val sel = ObjCRuntime.sel("lineSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLineSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paragraphSpacing
    override fun `paragraphSpacing`(): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setParagraphSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setParagraphSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property firstLineHeadIndent
    override fun `firstLineHeadIndent`(): CGFloat {
        val sel = ObjCRuntime.sel("firstLineHeadIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setFirstLineHeadIndent(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFirstLineHeadIndent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property headIndent
    override fun `headIndent`(): CGFloat {
        val sel = ObjCRuntime.sel("headIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setHeadIndent(value: CGFloat) {
        val sel = ObjCRuntime.sel("setHeadIndent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tailIndent
    override fun `tailIndent`(): CGFloat {
        val sel = ObjCRuntime.sel("tailIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTailIndent(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTailIndent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineBreakMode
    override fun `lineBreakMode`(): NSLineBreakMode {
        val sel = ObjCRuntime.sel("lineBreakMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakMode
    }
    fun setLineBreakMode(value: NSLineBreakMode) {
        val sel = ObjCRuntime.sel("setLineBreakMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumLineHeight
    override fun `minimumLineHeight`(): CGFloat {
        val sel = ObjCRuntime.sel("minimumLineHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumLineHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumLineHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumLineHeight
    override fun `maximumLineHeight`(): CGFloat {
        val sel = ObjCRuntime.sel("maximumLineHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaximumLineHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumLineHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property baseWritingDirection
    override fun `baseWritingDirection`(): NSWritingDirection {
        val sel = ObjCRuntime.sel("baseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    fun setBaseWritingDirection(value: NSWritingDirection) {
        val sel = ObjCRuntime.sel("setBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineHeightMultiple
    override fun `lineHeightMultiple`(): CGFloat {
        val sel = ObjCRuntime.sel("lineHeightMultiple")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLineHeightMultiple(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineHeightMultiple:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paragraphSpacingBefore
    override fun `paragraphSpacingBefore`(): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacingBefore")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setParagraphSpacingBefore(value: CGFloat) {
        val sel = ObjCRuntime.sel("setParagraphSpacingBefore:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hyphenationFactor
    override fun `hyphenationFactor`(): Float {
        val sel = ObjCRuntime.sel("hyphenationFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setHyphenationFactor(value: Float) {
        val sel = ObjCRuntime.sel("setHyphenationFactor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesDefaultHyphenation
    override fun `usesDefaultHyphenation`(): BOOL {
        val sel = ObjCRuntime.sel("usesDefaultHyphenation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesDefaultHyphenation(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesDefaultHyphenation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabStops
    /** @return NSArray<NSTextTab *> * */
    override fun `tabStops`(): MemorySegment {
        val sel = ObjCRuntime.sel("tabStops")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTabStops(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTabStops:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultTabInterval
    override fun `defaultTabInterval`(): CGFloat {
        val sel = ObjCRuntime.sel("defaultTabInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setDefaultTabInterval(value: CGFloat) {
        val sel = ObjCRuntime.sel("setDefaultTabInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsDefaultTighteningForTruncation
    override fun `allowsDefaultTighteningForTruncation`(): BOOL {
        val sel = ObjCRuntime.sel("allowsDefaultTighteningForTruncation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsDefaultTighteningForTruncation(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsDefaultTighteningForTruncation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineBreakStrategy
    override fun `lineBreakStrategy`(): NSLineBreakStrategy {
        val sel = ObjCRuntime.sel("lineBreakStrategy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakStrategy
    }
    fun setLineBreakStrategy(value: NSLineBreakStrategy) {
        val sel = ObjCRuntime.sel("setLineBreakStrategy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textLists
    /** @return NSArray<NSTextList *> * */
    override fun `textLists`(): MemorySegment {
        val sel = ObjCRuntime.sel("textLists")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextLists(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextLists:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category:  on NSMutableParagraphStyle ─────────────────────────────────────────

fun NSMutableParagraphStyle.alignment(): NSTextAlignment {
    val sel = ObjCRuntime.sel("alignment")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
}

fun NSMutableParagraphStyle.setAlignment(alignment: NSTextAlignment): Unit {
    val sel = ObjCRuntime.sel("setAlignment:")
    ObjCRuntime.msgSend(null, ptr, sel, alignment)
}

fun NSMutableParagraphStyle.tighteningFactorForTruncation(): Float {
    val sel = ObjCRuntime.sel("tighteningFactorForTruncation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

fun NSMutableParagraphStyle.setTighteningFactorForTruncation(tighteningFactorForTruncation: Float): Unit {
    val sel = ObjCRuntime.sel("setTighteningFactorForTruncation:")
    ObjCRuntime.msgSend(null, ptr, sel, tighteningFactorForTruncation)
}

/** @return NSArray<__kindof NSTextBlock *> * */
fun NSMutableParagraphStyle.textBlocks(): MemorySegment {
    val sel = ObjCRuntime.sel("textBlocks")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMutableParagraphStyle.setTextBlocks(textBlocks: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTextBlocks:")
    ObjCRuntime.msgSend(null, ptr, sel, textBlocks)
}

fun NSMutableParagraphStyle.headerLevel(): NSInteger {
    val sel = ObjCRuntime.sel("headerLevel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSMutableParagraphStyle.setHeaderLevel(headerLevel: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setHeaderLevel:")
    ObjCRuntime.msgSend(null, ptr, sel, headerLevel)
}

// @property alignment
    val sel = ObjCRuntime.sel("alignment")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
}
    val sel = ObjCRuntime.sel("setAlignment:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property tighteningFactorForTruncation
    val sel = ObjCRuntime.sel("tighteningFactorForTruncation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}
    val sel = ObjCRuntime.sel("setTighteningFactorForTruncation:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property textBlocks
/** @return NSArray<__kindof NSTextBlock *> * */
    val sel = ObjCRuntime.sel("textBlocks")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setTextBlocks:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property headerLevel
    val sel = ObjCRuntime.sel("headerLevel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}
    val sel = ObjCRuntime.sel("setHeaderLevel:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

