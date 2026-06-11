/**
 * Kotlin/JVM wrapper for Objective-C class: NSAttributedString
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding
 */
open class NSAttributedString(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAttributedString") }
        
    }
    
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    fun attributesAtIndex_effectiveRange(location: NSUInteger, range: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("attributesAtIndex:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, range) as MemorySegment
    }
    
    // @property string
    fun string(): MemorySegment {
        val sel = ObjCRuntime.sel("string")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringAsString(): String = ObjCRuntime.toJavaString(string())
    
}

// ── Category: NSExtendedAttributedString on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.attribute_atIndex_effectiveRange(attrName: NSAttributedStringKey, location: NSUInteger, range: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("attribute:atIndex:effectiveRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrName, location, range) as MemorySegment
}

fun NSAttributedString.attributedSubstringFromRange(range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("attributedSubstringFromRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSAttributedString.attributesAtIndex_longestEffectiveRange_inRange(location: NSUInteger, range: MemorySegment, rangeLimit: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("attributesAtIndex:longestEffectiveRange:inRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, range, rangeLimit) as MemorySegment
}

fun NSAttributedString.attribute_atIndex_longestEffectiveRange_inRange(attrName: NSAttributedStringKey, location: NSUInteger, range: MemorySegment, rangeLimit: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("attribute:atIndex:longestEffectiveRange:inRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrName, location, range, rangeLimit) as MemorySegment
}

fun NSAttributedString.isEqualToAttributedString(other: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToAttributedString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
}

fun NSAttributedString.initWithString(str: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str) as MemorySegment
}

fun NSAttributedString.initWithString_attributes(str: MemorySegment, attrs: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithString:attributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str, attrs) as MemorySegment
}

fun NSAttributedString.initWithAttributedString(attrStr: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithAttributedString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrStr) as MemorySegment
}

fun NSAttributedString.enumerateAttributesInRange_options_usingBlock(enumerationRange: NSRange, opts: NSAttributedStringEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateAttributesInRange:options:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, enumerationRange, opts, block)
}

fun NSAttributedString.enumerateAttribute_inRange_options_usingBlock(attrName: NSAttributedStringKey, enumerationRange: NSRange, opts: NSAttributedStringEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateAttribute:inRange:options:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, attrName, enumerationRange, opts, block)
}

fun NSAttributedString.length(): NSUInteger {
    val sel = ObjCRuntime.sel("length")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// @property length
fun NSAttributedString.length(): NSUInteger {
    val sel = ObjCRuntime.sel("length")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// ── Category: NSAttributedStringCreateFromMarkdown on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.initWithContentsOfMarkdownFileAtURL_options_baseURL_error(markdownFile: MemorySegment, options: MemorySegment, baseURL: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfMarkdownFileAtURL:options:baseURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, markdownFile, options, baseURL, error) as MemorySegment
}

fun NSAttributedString.initWithMarkdown_options_baseURL_error(markdown: MemorySegment, options: MemorySegment, baseURL: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithMarkdown:options:baseURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, markdown, options, baseURL, error) as MemorySegment
}

fun NSAttributedString.initWithMarkdownString_options_baseURL_error(markdownString: MemorySegment, options: MemorySegment, baseURL: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithMarkdownString:options:baseURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, markdownString, options, baseURL, error) as MemorySegment
}

// ── Category: NSAttributedStringFormatting on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.initWithFormat_options_locale(format: MemorySegment, options: NSAttributedStringFormattingOptions, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:options:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, options, locale) as MemorySegment
}

fun NSAttributedString.initWithFormat_options_locale_arguments(format: MemorySegment, options: NSAttributedStringFormattingOptions, locale: MemorySegment, arguments: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:options:locale:arguments:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, options, locale, arguments) as MemorySegment
}

fun NSAttributedString.initWithFormat_options_locale_context(format: MemorySegment, options: NSAttributedStringFormattingOptions, locale: MemorySegment, context: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:options:locale:context:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, options, locale, context) as MemorySegment
}

fun NSAttributedString.initWithFormat_options_locale_context_arguments(format: MemorySegment, options: NSAttributedStringFormattingOptions, locale: MemorySegment, context: MemorySegment, arguments: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:options:locale:context:arguments:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, options, locale, context, arguments) as MemorySegment
}

// Class method: +[NSAttributedString localizedAttributedStringWithFormat:]
fun NSAttributedString_localizedAttributedStringWithFormat(format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedAttributedStringWithFormat:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format) as MemorySegment
}

// Class method: +[NSAttributedString localizedAttributedStringWithFormat:options:]
fun NSAttributedString_localizedAttributedStringWithFormat_options(format: MemorySegment, options: NSAttributedStringFormattingOptions): MemorySegment {
    val sel = ObjCRuntime.sel("localizedAttributedStringWithFormat:options:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format, options) as MemorySegment
}

// Class method: +[NSAttributedString localizedAttributedStringWithFormat:context:]
fun NSAttributedString_localizedAttributedStringWithFormat_context(format: MemorySegment, context: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedAttributedStringWithFormat:context:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format, context) as MemorySegment
}

// Class method: +[NSAttributedString localizedAttributedStringWithFormat:options:context:]
fun NSAttributedString_localizedAttributedStringWithFormat_options_context(format: MemorySegment, options: NSAttributedStringFormattingOptions, context: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedAttributedStringWithFormat:options:context:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format, options, context) as MemorySegment
}

// ── Category: NSMorphology on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.attributedStringByInflectingString(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringByInflectingString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSAttributedStringDocumentFormats on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.initWithURL_options_documentAttributes_error(url: MemorySegment, options: MemorySegment, dict: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithURL:options:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, options, dict, error) as MemorySegment
}

fun NSAttributedString.initWithData_options_documentAttributes_error(`data`: MemorySegment, options: MemorySegment, dict: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithData:options:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, options, dict, error) as MemorySegment
}

fun NSAttributedString.dataFromRange_documentAttributes_error(range: NSRange, dict: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dataFromRange:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict, error) as MemorySegment
}

fun NSAttributedString.fileWrapperFromRange_documentAttributes_error(range: NSRange, dict: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("fileWrapperFromRange:documentAttributes:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict, error) as MemorySegment
}

// ── Category: NSAttributedStringKitAdditions on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.containsAttachmentsInRange(range: NSRange): BOOL {
    val sel = ObjCRuntime.sel("containsAttachmentsInRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, range) as BOOL
}

fun NSAttributedString.prefersRTFDInRange(range: NSRange): BOOL {
    val sel = ObjCRuntime.sel("prefersRTFDInRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, range) as BOOL
}

// ── Category: NSAttributedStringAppKitDocumentFormats on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.initWithRTF_documentAttributes(`data`: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithRTF:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, dict) as MemorySegment
}

fun NSAttributedString.initWithRTFD_documentAttributes(`data`: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithRTFD:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, dict) as MemorySegment
}

fun NSAttributedString.initWithHTML_documentAttributes(`data`: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithHTML:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, dict) as MemorySegment
}

fun NSAttributedString.initWithHTML_baseURL_documentAttributes(`data`: MemorySegment, base: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithHTML:baseURL:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, base, dict) as MemorySegment
}

fun NSAttributedString.initWithDocFormat_documentAttributes(`data`: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithDocFormat:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, dict) as MemorySegment
}

fun NSAttributedString.initWithHTML_options_documentAttributes(`data`: MemorySegment, options: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithHTML:options:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, options, dict) as MemorySegment
}

fun NSAttributedString.initWithRTFDFileWrapper_documentAttributes(wrapper: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithRTFDFileWrapper:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, wrapper, dict) as MemorySegment
}

fun NSAttributedString.RTFFromRange_documentAttributes(range: NSRange, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("RTFFromRange:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict) as MemorySegment
}

fun NSAttributedString.RTFDFromRange_documentAttributes(range: NSRange, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("RTFDFromRange:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict) as MemorySegment
}

fun NSAttributedString.RTFDFileWrapperFromRange_documentAttributes(range: NSRange, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("RTFDFileWrapperFromRange:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict) as MemorySegment
}

fun NSAttributedString.docFormatFromRange_documentAttributes(range: NSRange, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("docFormatFromRange:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, dict) as MemorySegment
}

// ── Category: NSAttributedStringAppKitAdditions on NSAttributedString ─────────────────────────────────────────

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSAttributedString.fontAttributesInRange(range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("fontAttributesInRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSAttributedString.rulerAttributesInRange(range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("rulerAttributesInRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
}

fun NSAttributedString.lineBreakBeforeIndex_withinRange(location: NSUInteger, aRange: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("lineBreakBeforeIndex:withinRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, location, aRange) as NSUInteger
}

fun NSAttributedString.lineBreakByHyphenatingBeforeIndex_withinRange(location: NSUInteger, aRange: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("lineBreakByHyphenatingBeforeIndex:withinRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, location, aRange) as NSUInteger
}

fun NSAttributedString.doubleClickAtIndex(location: NSUInteger): NSRange {
    val sel = ObjCRuntime.sel("doubleClickAtIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, location) as NSRange
}

fun NSAttributedString.nextWordFromIndex_forward(location: NSUInteger, isForward: BOOL): NSUInteger {
    val sel = ObjCRuntime.sel("nextWordFromIndex:forward:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, location, isForward) as NSUInteger
}

fun NSAttributedString.rangeOfTextBlock_atIndex(block: MemorySegment, location: NSUInteger): NSRange {
    val sel = ObjCRuntime.sel("rangeOfTextBlock:atIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, block, location) as NSRange
}

fun NSAttributedString.rangeOfTextTable_atIndex(table: MemorySegment, location: NSUInteger): NSRange {
    val sel = ObjCRuntime.sel("rangeOfTextTable:atIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, table, location) as NSRange
}

fun NSAttributedString.rangeOfTextList_atIndex(list: MemorySegment, location: NSUInteger): NSRange {
    val sel = ObjCRuntime.sel("rangeOfTextList:atIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, list, location) as NSRange
}

fun NSAttributedString.itemNumberInTextList_atIndex(list: MemorySegment, location: NSUInteger): NSInteger {
    val sel = ObjCRuntime.sel("itemNumberInTextList:atIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, list, location) as NSInteger
}

// ── Category: NSAttributedStringPasteboardAdditions on NSAttributedString ─────────────────────────────────────────

// Class method: +[NSAttributedString textTypes]
fun NSAttributedString_textTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSAttributedString textUnfilteredTypes]
fun NSAttributedString_textUnfilteredTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textUnfilteredTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property textTypes
/** @return NSArray<NSString *> * */
fun NSAttributedString.textTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property textUnfilteredTypes
/** @return NSArray<NSString *> * */
fun NSAttributedString.textUnfilteredTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textUnfilteredTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSDeprecatedKitAdditions on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.initWithURL_documentAttributes(url: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithURL:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, dict) as MemorySegment
}

fun NSAttributedString.initWithPath_documentAttributes(path: MemorySegment, dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithPath:documentAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, dict) as MemorySegment
}

fun NSAttributedString.URLAtIndex_effectiveRange(location: NSUInteger, effectiveRange: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("URLAtIndex:effectiveRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, effectiveRange) as MemorySegment
}

fun NSAttributedString.containsAttachments(): BOOL {
    val sel = ObjCRuntime.sel("containsAttachments")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class method: +[NSAttributedString textFileTypes]
fun NSAttributedString_textFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textFileTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSAttributedString textPasteboardTypes]
fun NSAttributedString_textPasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textPasteboardTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSAttributedString textUnfilteredFileTypes]
fun NSAttributedString_textUnfilteredFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textUnfilteredFileTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSAttributedString textUnfilteredPasteboardTypes]
fun NSAttributedString_textUnfilteredPasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("textUnfilteredPasteboardTypes")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property containsAttachments
fun NSAttributedString.containsAttachments(): BOOL {
    val sel = ObjCRuntime.sel("containsAttachments")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSAttributedStringAttachmentConveniences on NSAttributedString ─────────────────────────────────────────

// Class method: +[NSAttributedString attributedStringWithAttachment:]
fun NSAttributedString_attributedStringWithAttachment(attachment: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringWithAttachment:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, attachment) as MemorySegment
}

// Class method: +[NSAttributedString attributedStringWithAttachment:attributes:]
fun NSAttributedString_attributedStringWithAttachment_attributes(attachment: MemorySegment, attributes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringWithAttachment:attributes:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, attachment, attributes) as MemorySegment
}

// ── Category: NSStringDrawing on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.size(): CGSize {
    val sel = ObjCRuntime.sel("size")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as CGSize
}

fun NSAttributedString.drawAtPoint(point: CGPoint): Unit {
    val sel = ObjCRuntime.sel("drawAtPoint:")
    ObjCRuntime.msgSend(null, ptr, sel, point)
}

fun NSAttributedString.drawInRect(rect: CGRect): Unit {
    val sel = ObjCRuntime.sel("drawInRect:")
    ObjCRuntime.msgSend(null, ptr, sel, rect)
}

// ── Category: NSExtendedStringDrawing on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.drawWithRect_options_context(rect: CGRect, options: NSStringDrawingOptions, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawWithRect:options:context:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, options, context)
}

fun NSAttributedString.boundingRectWithSize_options_context(size: CGSize, options: NSStringDrawingOptions, context: MemorySegment): CGRect {
    val sel = ObjCRuntime.sel("boundingRectWithSize:options:context:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, size, options, context) as CGRect
}

// ── Category: NSStringDrawingDeprecated on NSAttributedString ─────────────────────────────────────────

fun NSAttributedString.drawWithRect_options(rect: NSRect, options: NSStringDrawingOptions): Unit {
    val sel = ObjCRuntime.sel("drawWithRect:options:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, options)
}

fun NSAttributedString.boundingRectWithSize_options(size: NSSize, options: NSStringDrawingOptions): NSRect {
    val sel = ObjCRuntime.sel("boundingRectWithSize:options:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, size, options) as NSRect
}

// ── Category: NSAttributedStringAdaptiveImageGlyphConveniences on NSAttributedString ─────────────────────────────────────────

// Class method: +[NSAttributedString attributedStringWithAdaptiveImageGlyph:attributes:]
fun NSAttributedString_attributedStringWithAdaptiveImageGlyph_attributes(adaptiveImageGlyph: MemorySegment, attributes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringWithAdaptiveImageGlyph:attributes:")
    val cls = ObjCRuntime.getClass("NSAttributedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, adaptiveImageGlyph, attributes) as MemorySegment
}

