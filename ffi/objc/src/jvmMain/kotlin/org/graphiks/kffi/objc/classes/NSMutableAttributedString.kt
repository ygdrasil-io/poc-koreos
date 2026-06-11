/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableAttributedString
 * Superclass: NSAttributedString
 */
open class NSMutableAttributedString(ptr: MemorySegment) : NSAttributedString(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableAttributedString") }
        
    }
    
    fun replaceCharactersInRange_withString(range: NSRange, str: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceCharactersInRange:withString:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), str)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun replaceCharactersInRange_withString(range: NSRange, str: String): Unit = replaceCharactersInRange_withString(range, ObjCRuntime.newNSString(Arena.global(), str))
    
    fun setAttributes_range(attrs: MemorySegment, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setAttributes:range:")
        ObjCRuntime.msgSend(null, ptr, sel, attrs, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
}

// ── Category: NSExtendedMutableAttributedString on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.addAttribute_value_range(name: NSAttributedStringKey, value: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("addAttribute:value:range:")
    ObjCRuntime.msgSend(null, ptr, sel, name, value, range)
}

fun NSMutableAttributedString.addAttributes_range(attrs: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("addAttributes:range:")
    ObjCRuntime.msgSend(null, ptr, sel, attrs, range)
}

fun NSMutableAttributedString.removeAttribute_range(name: NSAttributedStringKey, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("removeAttribute:range:")
    ObjCRuntime.msgSend(null, ptr, sel, name, range)
}

fun NSMutableAttributedString.replaceCharactersInRange_withAttributedString(range: NSRange, attrString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceCharactersInRange:withAttributedString:")
    ObjCRuntime.msgSend(null, ptr, sel, range, attrString)
}

fun NSMutableAttributedString.insertAttributedString_atIndex(attrString: MemorySegment, loc: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("insertAttributedString:atIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, attrString, loc)
}

fun NSMutableAttributedString.appendAttributedString(attrString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendAttributedString:")
    ObjCRuntime.msgSend(null, ptr, sel, attrString)
}

fun NSMutableAttributedString.deleteCharactersInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("deleteCharactersInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.setAttributedString(attrString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedString:")
    ObjCRuntime.msgSend(null, ptr, sel, attrString)
}

fun NSMutableAttributedString.beginEditing(): Unit {
    val sel = ObjCRuntime.sel("beginEditing")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSMutableAttributedString.endEditing(): Unit {
    val sel = ObjCRuntime.sel("endEditing")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSMutableAttributedString.mutableString(): MemorySegment {
    val sel = ObjCRuntime.sel("mutableString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property mutableString
fun NSMutableAttributedString.mutableString(): MemorySegment {
    val sel = ObjCRuntime.sel("mutableString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSMutableAttributedStringFormatting on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.appendLocalizedFormat(format: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendLocalizedFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, format)
}

// ── Category: NSAttributedStringAttributeFixing on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.fixAttributesInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("fixAttributesInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

// ── Category: NSMutableAttributedStringDocumentFormats on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.readFromURL_options_documentAttributes_error(url: MemorySegment, opts: MemorySegment, dict: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("readFromURL:options:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, opts, dict, error) as BOOL
}

fun NSMutableAttributedString.readFromData_options_documentAttributes_error(`data`: MemorySegment, opts: MemorySegment, dict: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("readFromData:options:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`, opts, dict, error) as BOOL
}

// ── Category: NSAttributedStringAppKitAttributeFixing on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.fixFontAttributeInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("fixFontAttributeInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.fixParagraphStyleAttributeInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("fixParagraphStyleAttributeInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.fixAttachmentAttributeInRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("fixAttachmentAttributeInRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

// ── Category: NSMutableAttributedStringAppKitAdditions on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.superscriptRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("superscriptRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.subscriptRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("subscriptRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.unscriptRange(range: NSRange): Unit {
    val sel = ObjCRuntime.sel("unscriptRange:")
    ObjCRuntime.msgSend(null, ptr, sel, range)
}

fun NSMutableAttributedString.applyFontTraits_range(traitMask: NSFontTraitMask, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("applyFontTraits:range:")
    ObjCRuntime.msgSend(null, ptr, sel, traitMask, range)
}

fun NSMutableAttributedString.setAlignment_range(alignment: NSTextAlignment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("setAlignment:range:")
    ObjCRuntime.msgSend(null, ptr, sel, alignment, range)
}

fun NSMutableAttributedString.setBaseWritingDirection_range(writingDirection: NSWritingDirection, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("setBaseWritingDirection:range:")
    ObjCRuntime.msgSend(null, ptr, sel, writingDirection, range)
}

// ── Category: NSDeprecatedKitAdditions on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.readFromURL_options_documentAttributes(url: MemorySegment, options: MemorySegment, dict: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("readFromURL:options:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, options, dict) as BOOL
}

fun NSMutableAttributedString.readFromData_options_documentAttributes(`data`: MemorySegment, options: MemorySegment, dict: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("readFromData:options:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`, options, dict) as BOOL
}

// ── Category: NSMutableAttributedStringAttachmentConveniences on NSMutableAttributedString ─────────────────────────────────────────

fun NSMutableAttributedString.updateAttachmentsFromPath(path: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("updateAttachmentsFromPath:")
    ObjCRuntime.msgSend(null, ptr, sel, path)
}

