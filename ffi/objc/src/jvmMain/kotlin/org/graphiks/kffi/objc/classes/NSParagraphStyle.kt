package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSParagraphStyle
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding
 */
open class NSParagraphStyle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSParagraphStyle") }
        
        open fun defaultWritingDirectionForLanguage(languageName: MemorySegment): NSWritingDirection {
            val sel = ObjCRuntime.sel("defaultWritingDirectionForLanguage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, languageName) as NSWritingDirection
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun defaultWritingDirectionForLanguage(languageName: String): NSWritingDirection = defaultWritingDirectionForLanguage(ObjCRuntime.newNSString(Arena.global(), languageName))
        
        open fun defaultParagraphStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultParagraphStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property defaultParagraphStyle
    }
    
    // @property lineSpacing
    open fun lineSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("lineSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property paragraphSpacing
    open fun paragraphSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property headIndent
    open fun headIndent(): CGFloat {
        val sel = ObjCRuntime.sel("headIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property tailIndent
    open fun tailIndent(): CGFloat {
        val sel = ObjCRuntime.sel("tailIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property firstLineHeadIndent
    open fun firstLineHeadIndent(): CGFloat {
        val sel = ObjCRuntime.sel("firstLineHeadIndent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property minimumLineHeight
    open fun minimumLineHeight(): CGFloat {
        val sel = ObjCRuntime.sel("minimumLineHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property maximumLineHeight
    open fun maximumLineHeight(): CGFloat {
        val sel = ObjCRuntime.sel("maximumLineHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property lineBreakMode
    open fun lineBreakMode(): NSLineBreakMode {
        val sel = ObjCRuntime.sel("lineBreakMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakMode
    }
    
    // @property baseWritingDirection
    open fun baseWritingDirection(): NSWritingDirection {
        val sel = ObjCRuntime.sel("baseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    
    // @property lineHeightMultiple
    open fun lineHeightMultiple(): CGFloat {
        val sel = ObjCRuntime.sel("lineHeightMultiple")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property paragraphSpacingBefore
    open fun paragraphSpacingBefore(): CGFloat {
        val sel = ObjCRuntime.sel("paragraphSpacingBefore")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property hyphenationFactor
    open fun hyphenationFactor(): Float {
        val sel = ObjCRuntime.sel("hyphenationFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property usesDefaultHyphenation
    open fun usesDefaultHyphenation(): BOOL {
        val sel = ObjCRuntime.sel("usesDefaultHyphenation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property tabStops
    /** @return NSArray<NSTextTab *> * */
    open fun tabStops(): MemorySegment {
        val sel = ObjCRuntime.sel("tabStops")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaultTabInterval
    open fun defaultTabInterval(): CGFloat {
        val sel = ObjCRuntime.sel("defaultTabInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property textLists
    /** @return NSArray<NSTextList *> * */
    open fun textLists(): MemorySegment {
        val sel = ObjCRuntime.sel("textLists")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allowsDefaultTighteningForTruncation
    open fun allowsDefaultTighteningForTruncation(): BOOL {
        val sel = ObjCRuntime.sel("allowsDefaultTighteningForTruncation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property lineBreakStrategy
    open fun lineBreakStrategy(): NSLineBreakStrategy {
        val sel = ObjCRuntime.sel("lineBreakStrategy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakStrategy
    }
    
}

// ── Category:  on NSParagraphStyle ─────────────────────────────────────────

fun NSParagraphStyle.alignment(): NSTextAlignment {
    val sel = ObjCRuntime.sel("alignment")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
}

fun NSParagraphStyle.tighteningFactorForTruncation(): Float {
    val sel = ObjCRuntime.sel("tighteningFactorForTruncation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

/** @return NSArray<__kindof NSTextBlock *> * */
fun NSParagraphStyle.textBlocks(): MemorySegment {
    val sel = ObjCRuntime.sel("textBlocks")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSParagraphStyle.headerLevel(): NSInteger {
    val sel = ObjCRuntime.sel("headerLevel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// @property alignment
    val sel = ObjCRuntime.sel("alignment")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
}

// @property tighteningFactorForTruncation
    val sel = ObjCRuntime.sel("tighteningFactorForTruncation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

// @property textBlocks
/** @return NSArray<__kindof NSTextBlock *> * */
    val sel = ObjCRuntime.sel("textBlocks")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property headerLevel
    val sel = ObjCRuntime.sel("headerLevel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

