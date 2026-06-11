/**
 * Kotlin/JVM wrapper for Objective-C class: NSString
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding
 */
open class NSString(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSString") }
        
    }
    
    fun characterAtIndex(index: NSUInteger): unichar {
        val sel = ObjCRuntime.sel("characterAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel, index) as unichar
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property length
    fun length(): NSUInteger {
        val sel = ObjCRuntime.sel("length")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

// ── Category: NSStringExtensionMethods on NSString ─────────────────────────────────────────

fun NSString.substringFromIndex(from: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("substringFromIndex:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, from) as MemorySegment
}

fun NSString.substringToIndex(to: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("substringToIndex:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, to) as MemorySegment
}

fun NSString.substringWithRange(range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("substringWithRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
}

fun NSString.getCharacters_range(buffer: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("getCharacters:range:")
    ObjCRuntime.msgSend(null, ptr, sel, buffer, range)
}

fun NSString.compare(string: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("compare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as NSComparisonResult
}

fun NSString.compare_options(string: MemorySegment, mask: NSStringCompareOptions): NSComparisonResult {
    val sel = ObjCRuntime.sel("compare:options:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, mask) as NSComparisonResult
}

fun NSString.compare_options_range(string: MemorySegment, mask: NSStringCompareOptions, rangeOfReceiverToCompare: NSRange): NSComparisonResult {
    val sel = ObjCRuntime.sel("compare:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, mask, rangeOfReceiverToCompare) as NSComparisonResult
}

fun NSString.compare_options_range_locale(string: MemorySegment, mask: NSStringCompareOptions, rangeOfReceiverToCompare: NSRange, locale: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("compare:options:range:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, mask, rangeOfReceiverToCompare, locale) as NSComparisonResult
}

fun NSString.caseInsensitiveCompare(string: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("caseInsensitiveCompare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as NSComparisonResult
}

fun NSString.localizedCompare(string: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("localizedCompare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as NSComparisonResult
}

fun NSString.localizedCaseInsensitiveCompare(string: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("localizedCaseInsensitiveCompare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as NSComparisonResult
}

fun NSString.localizedStandardCompare(string: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("localizedStandardCompare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as NSComparisonResult
}

fun NSString.isEqualToString(aString: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aString) as BOOL
}

fun NSString.hasPrefix(str: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("hasPrefix:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, str) as BOOL
}

fun NSString.hasSuffix(str: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("hasSuffix:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, str) as BOOL
}

fun NSString.commonPrefixWithString_options(str: MemorySegment, mask: NSStringCompareOptions): MemorySegment {
    val sel = ObjCRuntime.sel("commonPrefixWithString:options:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str, mask) as MemorySegment
}

fun NSString.containsString(str: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("containsString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, str) as BOOL
}

fun NSString.localizedCaseInsensitiveContainsString(str: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("localizedCaseInsensitiveContainsString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, str) as BOOL
}

fun NSString.localizedStandardContainsString(str: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("localizedStandardContainsString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, str) as BOOL
}

fun NSString.localizedStandardRangeOfString(str: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("localizedStandardRangeOfString:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, str) as NSRange
}

fun NSString.rangeOfString(searchString: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("rangeOfString:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchString) as NSRange
}

fun NSString.rangeOfString_options(searchString: MemorySegment, mask: NSStringCompareOptions): NSRange {
    val sel = ObjCRuntime.sel("rangeOfString:options:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchString, mask) as NSRange
}

fun NSString.rangeOfString_options_range(searchString: MemorySegment, mask: NSStringCompareOptions, rangeOfReceiverToSearch: NSRange): NSRange {
    val sel = ObjCRuntime.sel("rangeOfString:options:range:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchString, mask, rangeOfReceiverToSearch) as NSRange
}

fun NSString.rangeOfString_options_range_locale(searchString: MemorySegment, mask: NSStringCompareOptions, rangeOfReceiverToSearch: NSRange, locale: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("rangeOfString:options:range:locale:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchString, mask, rangeOfReceiverToSearch, locale) as NSRange
}

fun NSString.rangeOfCharacterFromSet(searchSet: MemorySegment): NSRange {
    val sel = ObjCRuntime.sel("rangeOfCharacterFromSet:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchSet) as NSRange
}

fun NSString.rangeOfCharacterFromSet_options(searchSet: MemorySegment, mask: NSStringCompareOptions): NSRange {
    val sel = ObjCRuntime.sel("rangeOfCharacterFromSet:options:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchSet, mask) as NSRange
}

fun NSString.rangeOfCharacterFromSet_options_range(searchSet: MemorySegment, mask: NSStringCompareOptions, rangeOfReceiverToSearch: NSRange): NSRange {
    val sel = ObjCRuntime.sel("rangeOfCharacterFromSet:options:range:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, searchSet, mask, rangeOfReceiverToSearch) as NSRange
}

fun NSString.rangeOfComposedCharacterSequenceAtIndex(index: NSUInteger): NSRange {
    val sel = ObjCRuntime.sel("rangeOfComposedCharacterSequenceAtIndex:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, index) as NSRange
}

fun NSString.rangeOfComposedCharacterSequencesForRange(range: NSRange): NSRange {
    val sel = ObjCRuntime.sel("rangeOfComposedCharacterSequencesForRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, range) as NSRange
}

fun NSString.stringByAppendingString(aString: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAppendingString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aString) as MemorySegment
}

fun NSString.stringByAppendingFormat(format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAppendingFormat:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format) as MemorySegment
}

fun NSString.uppercaseStringWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("uppercaseStringWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSString.lowercaseStringWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("lowercaseStringWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSString.capitalizedStringWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("capitalizedStringWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSString.getLineStart_end_contentsEnd_forRange(startPtr: MemorySegment, lineEndPtr: MemorySegment, contentsEndPtr: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("getLineStart:end:contentsEnd:forRange:")
    ObjCRuntime.msgSend(null, ptr, sel, startPtr, lineEndPtr, contentsEndPtr, range)
}

fun NSString.lineRangeForRange(range: NSRange): NSRange {
    val sel = ObjCRuntime.sel("lineRangeForRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, range) as NSRange
}

fun NSString.getParagraphStart_end_contentsEnd_forRange(startPtr: MemorySegment, parEndPtr: MemorySegment, contentsEndPtr: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("getParagraphStart:end:contentsEnd:forRange:")
    ObjCRuntime.msgSend(null, ptr, sel, startPtr, parEndPtr, contentsEndPtr, range)
}

fun NSString.paragraphRangeForRange(range: NSRange): NSRange {
    val sel = ObjCRuntime.sel("paragraphRangeForRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, range) as NSRange
}

fun NSString.enumerateSubstringsInRange_options_usingBlock(range: NSRange, opts: NSStringEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateSubstringsInRange:options:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, range, opts, block)
}

fun NSString.enumerateLinesUsingBlock(block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateLinesUsingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, block)
}

fun NSString.dataUsingEncoding_allowLossyConversion(encoding: NSStringEncoding, lossy: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("dataUsingEncoding:allowLossyConversion:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, encoding, lossy) as MemorySegment
}

fun NSString.dataUsingEncoding(encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("dataUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, encoding) as MemorySegment
}

fun NSString.canBeConvertedToEncoding(encoding: NSStringEncoding): BOOL {
    val sel = ObjCRuntime.sel("canBeConvertedToEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, encoding) as BOOL
}

fun NSString.cStringUsingEncoding(encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("cStringUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, encoding) as MemorySegment
}

fun NSString.getCString_maxLength_encoding(buffer: MemorySegment, maxBufferCount: NSUInteger, encoding: NSStringEncoding): BOOL {
    val sel = ObjCRuntime.sel("getCString:maxLength:encoding:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, buffer, maxBufferCount, encoding) as BOOL
}

fun NSString.getBytes_maxLength_usedLength_encoding_options_range_remainingRange(buffer: MemorySegment, maxBufferCount: NSUInteger, usedBufferCount: MemorySegment, encoding: NSStringEncoding, options: NSStringEncodingConversionOptions, range: NSRange, leftover: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("getBytes:maxLength:usedLength:encoding:options:range:remainingRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, buffer, maxBufferCount, usedBufferCount, encoding, options, range, leftover) as BOOL
}

fun NSString.maximumLengthOfBytesUsingEncoding(enc: NSStringEncoding): NSUInteger {
    val sel = ObjCRuntime.sel("maximumLengthOfBytesUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, enc) as NSUInteger
}

fun NSString.lengthOfBytesUsingEncoding(enc: NSStringEncoding): NSUInteger {
    val sel = ObjCRuntime.sel("lengthOfBytesUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, enc) as NSUInteger
}

/** @return NSArray<NSString *> * */
fun NSString.componentsSeparatedByString(separator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("componentsSeparatedByString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, separator) as MemorySegment
}

/** @return NSArray<NSString *> * */
fun NSString.componentsSeparatedByCharactersInSet(separator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("componentsSeparatedByCharactersInSet:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, separator) as MemorySegment
}

fun NSString.stringByTrimmingCharactersInSet(`set`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByTrimmingCharactersInSet:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `set`) as MemorySegment
}

fun NSString.stringByPaddingToLength_withString_startingAtIndex(newLength: NSUInteger, padString: MemorySegment, padIndex: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("stringByPaddingToLength:withString:startingAtIndex:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newLength, padString, padIndex) as MemorySegment
}

fun NSString.stringByFoldingWithOptions_locale(options: NSStringCompareOptions, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByFoldingWithOptions:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options, locale) as MemorySegment
}

fun NSString.stringByReplacingOccurrencesOfString_withString_options_range(target: MemorySegment, replacement: MemorySegment, options: NSStringCompareOptions, searchRange: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("stringByReplacingOccurrencesOfString:withString:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, replacement, options, searchRange) as MemorySegment
}

fun NSString.stringByReplacingOccurrencesOfString_withString(target: MemorySegment, replacement: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByReplacingOccurrencesOfString:withString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, replacement) as MemorySegment
}

fun NSString.stringByReplacingCharactersInRange_withString(range: NSRange, replacement: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByReplacingCharactersInRange:withString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, replacement) as MemorySegment
}

fun NSString.stringByApplyingTransform_reverse(transform: NSStringTransform, reverse: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("stringByApplyingTransform:reverse:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, transform, reverse) as MemorySegment
}

fun NSString.writeToURL_atomically_encoding_error(url: MemorySegment, useAuxiliaryFile: BOOL, enc: NSStringEncoding, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:atomically:encoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, useAuxiliaryFile, enc, error) as BOOL
}

fun NSString.writeToFile_atomically_encoding_error(path: MemorySegment, useAuxiliaryFile: BOOL, enc: NSStringEncoding, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeToFile:atomically:encoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, useAuxiliaryFile, enc, error) as BOOL
}

fun NSString.initWithCharactersNoCopy_length_freeWhenDone(characters: MemorySegment, length: NSUInteger, freeBuffer: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCharactersNoCopy:length:freeWhenDone:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, characters, length, freeBuffer) as MemorySegment
}

fun NSString.initWithCharactersNoCopy_length_deallocator(chars: MemorySegment, len: NSUInteger, deallocator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCharactersNoCopy:length:deallocator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, chars, len, deallocator) as MemorySegment
}

fun NSString.initWithCharacters_length(characters: MemorySegment, length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCharacters:length:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, characters, length) as MemorySegment
}

fun NSString.initWithUTF8String(nullTerminatedCString: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithUTF8String:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, nullTerminatedCString) as MemorySegment
}

fun NSString.initWithString(aString: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aString) as MemorySegment
}

fun NSString.initWithFormat(format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format) as MemorySegment
}

fun NSString.initWithFormat_arguments(format: MemorySegment, argList: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:arguments:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, argList) as MemorySegment
}

fun NSString.initWithFormat_locale(format: MemorySegment, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, locale) as MemorySegment
}

fun NSString.initWithFormat_locale_arguments(format: MemorySegment, locale: MemorySegment, argList: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithFormat:locale:arguments:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, locale, argList) as MemorySegment
}

fun NSString.initWithValidatedFormat_validFormatSpecifiers_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithValidatedFormat:validFormatSpecifiers:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, validFormatSpecifiers, error) as MemorySegment
}

fun NSString.initWithValidatedFormat_validFormatSpecifiers_locale_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, locale: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithValidatedFormat:validFormatSpecifiers:locale:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, validFormatSpecifiers, locale, error) as MemorySegment
}

fun NSString.initWithValidatedFormat_validFormatSpecifiers_arguments_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, argList: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithValidatedFormat:validFormatSpecifiers:arguments:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, validFormatSpecifiers, argList, error) as MemorySegment
}

fun NSString.initWithValidatedFormat_validFormatSpecifiers_locale_arguments_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, locale: MemorySegment, argList: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithValidatedFormat:validFormatSpecifiers:locale:arguments:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, validFormatSpecifiers, locale, argList, error) as MemorySegment
}

fun NSString.initWithData_encoding(`data`: MemorySegment, encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("initWithData:encoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, encoding) as MemorySegment
}

fun NSString.initWithBytes_length_encoding(bytes: MemorySegment, len: NSUInteger, encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("initWithBytes:length:encoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes, len, encoding) as MemorySegment
}

fun NSString.initWithBytesNoCopy_length_encoding_freeWhenDone(bytes: MemorySegment, len: NSUInteger, encoding: NSStringEncoding, freeBuffer: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithBytesNoCopy:length:encoding:freeWhenDone:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes, len, encoding, freeBuffer) as MemorySegment
}

fun NSString.initWithBytesNoCopy_length_encoding_deallocator(bytes: MemorySegment, len: NSUInteger, encoding: NSStringEncoding, deallocator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithBytesNoCopy:length:encoding:deallocator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes, len, encoding, deallocator) as MemorySegment
}

fun NSString.initWithCString_encoding(nullTerminatedCString: MemorySegment, encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCString:encoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, nullTerminatedCString, encoding) as MemorySegment
}

fun NSString.initWithContentsOfURL_encoding_error(url: MemorySegment, enc: NSStringEncoding, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:encoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, enc, error) as MemorySegment
}

fun NSString.initWithContentsOfFile_encoding_error(path: MemorySegment, enc: NSStringEncoding, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:encoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, enc, error) as MemorySegment
}

fun NSString.initWithContentsOfURL_usedEncoding_error(url: MemorySegment, enc: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:usedEncoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, enc, error) as MemorySegment
}

fun NSString.initWithContentsOfFile_usedEncoding_error(path: MemorySegment, enc: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:usedEncoding:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, enc, error) as MemorySegment
}

fun NSString.doubleValue(): Double {
    val sel = ObjCRuntime.sel("doubleValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
}

fun NSString.floatValue(): Float {
    val sel = ObjCRuntime.sel("floatValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

fun NSString.intValue(): Int {
    val sel = ObjCRuntime.sel("intValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
}

fun NSString.integerValue(): NSInteger {
    val sel = ObjCRuntime.sel("integerValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSString.longLongValue(): Long {
    val sel = ObjCRuntime.sel("longLongValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
}

fun NSString.boolValue(): BOOL {
    val sel = ObjCRuntime.sel("boolValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSString.uppercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("uppercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.lowercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("lowercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.capitalizedString(): MemorySegment {
    val sel = ObjCRuntime.sel("capitalizedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.localizedUppercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedUppercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.localizedLowercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedLowercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.localizedCapitalizedString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedCapitalizedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.UTF8String(): MemorySegment {
    val sel = ObjCRuntime.sel("UTF8String")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.fastestEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("fastestEncoding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
}

fun NSString.smallestEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("smallestEncoding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
}

fun NSString.decomposedStringWithCanonicalMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("decomposedStringWithCanonicalMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.precomposedStringWithCanonicalMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("precomposedStringWithCanonicalMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.decomposedStringWithCompatibilityMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("decomposedStringWithCompatibilityMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.precomposedStringWithCompatibilityMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("precomposedStringWithCompatibilityMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.hash(): NSUInteger {
    val sel = ObjCRuntime.sel("hash")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// Class method: +[NSString localizedNameOfStringEncoding:]
fun NSString_localizedNameOfStringEncoding(encoding: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("localizedNameOfStringEncoding:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, encoding) as MemorySegment
}

// Class method: +[NSString string]
fun NSString_string(): MemorySegment {
    val sel = ObjCRuntime.sel("string")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSString stringWithString:]
fun NSString_stringWithString(string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithString:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string) as MemorySegment
}

// Class method: +[NSString stringWithCharacters:length:]
fun NSString_stringWithCharacters_length(characters: MemorySegment, length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithCharacters:length:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, characters, length) as MemorySegment
}

// Class method: +[NSString stringWithUTF8String:]
fun NSString_stringWithUTF8String(nullTerminatedCString: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithUTF8String:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, nullTerminatedCString) as MemorySegment
}

// Class method: +[NSString stringWithFormat:]
fun NSString_stringWithFormat(format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithFormat:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format) as MemorySegment
}

// Class method: +[NSString localizedStringWithFormat:]
fun NSString_localizedStringWithFormat(format: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedStringWithFormat:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format) as MemorySegment
}

// Class method: +[NSString stringWithValidatedFormat:validFormatSpecifiers:error:]
fun NSString_stringWithValidatedFormat_validFormatSpecifiers_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithValidatedFormat:validFormatSpecifiers:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format, validFormatSpecifiers, error) as MemorySegment
}

// Class method: +[NSString localizedStringWithValidatedFormat:validFormatSpecifiers:error:]
fun NSString_localizedStringWithValidatedFormat_validFormatSpecifiers_error(format: MemorySegment, validFormatSpecifiers: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedStringWithValidatedFormat:validFormatSpecifiers:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, format, validFormatSpecifiers, error) as MemorySegment
}

// Class method: +[NSString stringWithCString:encoding:]
fun NSString_stringWithCString_encoding(cString: MemorySegment, enc: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithCString:encoding:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, cString, enc) as MemorySegment
}

// Class method: +[NSString stringWithContentsOfURL:encoding:error:]
fun NSString_stringWithContentsOfURL_encoding_error(url: MemorySegment, enc: NSStringEncoding, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfURL:encoding:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, enc, error) as MemorySegment
}

// Class method: +[NSString stringWithContentsOfFile:encoding:error:]
fun NSString_stringWithContentsOfFile_encoding_error(path: MemorySegment, enc: NSStringEncoding, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfFile:encoding:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path, enc, error) as MemorySegment
}

// Class method: +[NSString stringWithContentsOfURL:usedEncoding:error:]
fun NSString_stringWithContentsOfURL_usedEncoding_error(url: MemorySegment, enc: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfURL:usedEncoding:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, enc, error) as MemorySegment
}

// Class method: +[NSString stringWithContentsOfFile:usedEncoding:error:]
fun NSString_stringWithContentsOfFile_usedEncoding_error(path: MemorySegment, enc: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfFile:usedEncoding:error:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path, enc, error) as MemorySegment
}

// Class method: +[NSString availableStringEncodings]
fun NSString_availableStringEncodings(): MemorySegment {
    val sel = ObjCRuntime.sel("availableStringEncodings")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSString defaultCStringEncoding]
fun NSString_defaultCStringEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("defaultCStringEncoding")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, cls, sel) as NSStringEncoding
}

// @property doubleValue
fun NSString.doubleValue(): Double {
    val sel = ObjCRuntime.sel("doubleValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
}

// @property floatValue
fun NSString.floatValue(): Float {
    val sel = ObjCRuntime.sel("floatValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
}

// @property intValue
fun NSString.intValue(): Int {
    val sel = ObjCRuntime.sel("intValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
}

// @property integerValue
fun NSString.integerValue(): NSInteger {
    val sel = ObjCRuntime.sel("integerValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// @property longLongValue
fun NSString.longLongValue(): Long {
    val sel = ObjCRuntime.sel("longLongValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
}

// @property boolValue
fun NSString.boolValue(): BOOL {
    val sel = ObjCRuntime.sel("boolValue")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property uppercaseString
fun NSString.uppercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("uppercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property lowercaseString
fun NSString.lowercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("lowercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property capitalizedString
fun NSString.capitalizedString(): MemorySegment {
    val sel = ObjCRuntime.sel("capitalizedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property localizedUppercaseString
fun NSString.localizedUppercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedUppercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property localizedLowercaseString
fun NSString.localizedLowercaseString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedLowercaseString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property localizedCapitalizedString
fun NSString.localizedCapitalizedString(): MemorySegment {
    val sel = ObjCRuntime.sel("localizedCapitalizedString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property UTF8String
fun NSString.UTF8String(): MemorySegment {
    val sel = ObjCRuntime.sel("UTF8String")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property fastestEncoding
fun NSString.fastestEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("fastestEncoding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
}

// @property smallestEncoding
fun NSString.smallestEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("smallestEncoding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
}

// @property availableStringEncodings
fun NSString.availableStringEncodings(): MemorySegment {
    val sel = ObjCRuntime.sel("availableStringEncodings")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property defaultCStringEncoding
fun NSString.defaultCStringEncoding(): NSStringEncoding {
    val sel = ObjCRuntime.sel("defaultCStringEncoding")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
}

// @property decomposedStringWithCanonicalMapping
fun NSString.decomposedStringWithCanonicalMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("decomposedStringWithCanonicalMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property precomposedStringWithCanonicalMapping
fun NSString.precomposedStringWithCanonicalMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("precomposedStringWithCanonicalMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property decomposedStringWithCompatibilityMapping
fun NSString.decomposedStringWithCompatibilityMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("decomposedStringWithCompatibilityMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property precomposedStringWithCompatibilityMapping
fun NSString.precomposedStringWithCompatibilityMapping(): MemorySegment {
    val sel = ObjCRuntime.sel("precomposedStringWithCompatibilityMapping")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property description
fun NSString.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property hash
fun NSString.hash(): NSUInteger {
    val sel = ObjCRuntime.sel("hash")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// ── Category: NSStringEncodingDetection on NSString ─────────────────────────────────────────

// Class method: +[NSString stringEncodingForData:encodingOptions:convertedString:usedLossyConversion:]
fun NSString_stringEncodingForData_encodingOptions_convertedString_usedLossyConversion(`data`: MemorySegment, opts: MemorySegment, string: MemorySegment, usedLossyConversion: MemorySegment): NSStringEncoding {
    val sel = ObjCRuntime.sel("stringEncodingForData:encodingOptions:convertedString:usedLossyConversion:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, cls, sel, `data`, opts, string, usedLossyConversion) as NSStringEncoding
}

// ── Category: NSItemProvider on NSString ─────────────────────────────────────────

// ── Category: NSExtendedStringPropertyListParsing on NSString ─────────────────────────────────────────

fun NSString.propertyList(): MemorySegment {
    val sel = ObjCRuntime.sel("propertyList")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.propertyListFromStringsFileFormat(): MemorySegment {
    val sel = ObjCRuntime.sel("propertyListFromStringsFileFormat")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSStringDeprecated on NSString ─────────────────────────────────────────

fun NSString.cString(): MemorySegment {
    val sel = ObjCRuntime.sel("cString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.lossyCString(): MemorySegment {
    val sel = ObjCRuntime.sel("lossyCString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.cStringLength(): NSUInteger {
    val sel = ObjCRuntime.sel("cStringLength")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSString.getCString(bytes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getCString:")
    ObjCRuntime.msgSend(null, ptr, sel, bytes)
}

fun NSString.getCString_maxLength(bytes: MemorySegment, maxLength: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("getCString:maxLength:")
    ObjCRuntime.msgSend(null, ptr, sel, bytes, maxLength)
}

fun NSString.getCString_maxLength_range_remainingRange(bytes: MemorySegment, maxLength: NSUInteger, aRange: NSRange, leftoverRange: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getCString:maxLength:range:remainingRange:")
    ObjCRuntime.msgSend(null, ptr, sel, bytes, maxLength, aRange, leftoverRange)
}

fun NSString.writeToFile_atomically(path: MemorySegment, useAuxiliaryFile: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToFile:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, useAuxiliaryFile) as BOOL
}

fun NSString.writeToURL_atomically(url: MemorySegment, atomically: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, atomically) as BOOL
}

fun NSString.initWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
}

fun NSString.initWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
}

fun NSString.initWithCStringNoCopy_length_freeWhenDone(bytes: MemorySegment, length: NSUInteger, freeBuffer: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCStringNoCopy:length:freeWhenDone:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes, length, freeBuffer) as MemorySegment
}

fun NSString.initWithCString_length(bytes: MemorySegment, length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCString:length:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes, length) as MemorySegment
}

fun NSString.initWithCString(bytes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithCString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bytes) as MemorySegment
}

fun NSString.getCharacters(buffer: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getCharacters:")
    ObjCRuntime.msgSend(null, ptr, sel, buffer)
}

// Class method: +[NSString stringWithContentsOfFile:]
fun NSString_stringWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfFile:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path) as MemorySegment
}

// Class method: +[NSString stringWithContentsOfURL:]
fun NSString_stringWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithContentsOfURL:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url) as MemorySegment
}

// Class method: +[NSString stringWithCString:length:]
fun NSString_stringWithCString_length(bytes: MemorySegment, length: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithCString:length:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, bytes, length) as MemorySegment
}

// Class method: +[NSString stringWithCString:]
fun NSString_stringWithCString(bytes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringWithCString:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, bytes) as MemorySegment
}

// ── Category: NSBundleExtensionMethods on NSString ─────────────────────────────────────────

fun NSString.variantFittingPresentationWidth(width: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("variantFittingPresentationWidth:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, width) as MemorySegment
}

// ── Category: NSStringPathExtensions on NSString ─────────────────────────────────────────

fun NSString.stringByAppendingPathComponent(str: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAppendingPathComponent:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str) as MemorySegment
}

fun NSString.stringByAppendingPathExtension(str: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAppendingPathExtension:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str) as MemorySegment
}

/** @return NSArray<NSString *> * */
fun NSString.stringsByAppendingPaths(paths: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringsByAppendingPaths:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, paths) as MemorySegment
}

fun NSString.completePathIntoString_caseSensitive_matchesIntoArray_filterTypes(outputName: MemorySegment, flag: BOOL, outputArray: MemorySegment, filterTypes: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("completePathIntoString:caseSensitive:matchesIntoArray:filterTypes:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, outputName, flag, outputArray, filterTypes) as NSUInteger
}

fun NSString.getFileSystemRepresentation_maxLength(cname: MemorySegment, max: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("getFileSystemRepresentation:maxLength:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, cname, max) as BOOL
}

/** @return NSArray<NSString *> * */
fun NSString.pathComponents(): MemorySegment {
    val sel = ObjCRuntime.sel("pathComponents")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.isAbsolutePath(): BOOL {
    val sel = ObjCRuntime.sel("isAbsolutePath")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSString.lastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("lastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByDeletingLastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByDeletingLastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.pathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("pathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByDeletingPathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByDeletingPathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByAbbreviatingWithTildeInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAbbreviatingWithTildeInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByExpandingTildeInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByExpandingTildeInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByStandardizingPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByStandardizingPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.stringByResolvingSymlinksInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByResolvingSymlinksInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSString.fileSystemRepresentation(): MemorySegment {
    val sel = ObjCRuntime.sel("fileSystemRepresentation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class method: +[NSString pathWithComponents:]
fun NSString_pathWithComponents(components: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("pathWithComponents:")
    val cls = ObjCRuntime.getClass("NSString")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, components) as MemorySegment
}

// @property pathComponents
/** @return NSArray<NSString *> * */
fun NSString.pathComponents(): MemorySegment {
    val sel = ObjCRuntime.sel("pathComponents")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property absolutePath
fun NSString.isAbsolutePath(): BOOL {
    val sel = ObjCRuntime.sel("isAbsolutePath")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property lastPathComponent
fun NSString.lastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("lastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByDeletingLastPathComponent
fun NSString.stringByDeletingLastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByDeletingLastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property pathExtension
fun NSString.pathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("pathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByDeletingPathExtension
fun NSString.stringByDeletingPathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByDeletingPathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByAbbreviatingWithTildeInPath
fun NSString.stringByAbbreviatingWithTildeInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAbbreviatingWithTildeInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByExpandingTildeInPath
fun NSString.stringByExpandingTildeInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByExpandingTildeInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByStandardizingPath
fun NSString.stringByStandardizingPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByStandardizingPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByResolvingSymlinksInPath
fun NSString.stringByResolvingSymlinksInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByResolvingSymlinksInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property fileSystemRepresentation
fun NSString.fileSystemRepresentation(): MemorySegment {
    val sel = ObjCRuntime.sel("fileSystemRepresentation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSURLUtilities on NSString ─────────────────────────────────────────

fun NSString.stringByAddingPercentEncodingWithAllowedCharacters(allowedCharacters: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAddingPercentEncodingWithAllowedCharacters:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, allowedCharacters) as MemorySegment
}

fun NSString.stringByAddingPercentEscapesUsingEncoding(enc: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("stringByAddingPercentEscapesUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, enc) as MemorySegment
}

fun NSString.stringByReplacingPercentEscapesUsingEncoding(enc: NSStringEncoding): MemorySegment {
    val sel = ObjCRuntime.sel("stringByReplacingPercentEscapesUsingEncoding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, enc) as MemorySegment
}

fun NSString.stringByRemovingPercentEncoding(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByRemovingPercentEncoding")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property stringByRemovingPercentEncoding
fun NSString.stringByRemovingPercentEncoding(): MemorySegment {
    val sel = ObjCRuntime.sel("stringByRemovingPercentEncoding")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSLinguisticAnalysis on NSString ─────────────────────────────────────────

/** @return NSArray<NSLinguisticTag> * */
fun NSString.linguisticTagsInRange_scheme_options_orthography_tokenRanges(range: NSRange, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, tokenRanges: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("linguisticTagsInRange:scheme:options:orthography:tokenRanges:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, scheme, options, orthography, tokenRanges) as MemorySegment
}

fun NSString.enumerateLinguisticTagsInRange_scheme_options_orthography_usingBlock(range: NSRange, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateLinguisticTagsInRange:scheme:options:orthography:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, range, scheme, options, orthography, block)
}

// ── Category: NSPasteboardSupport on NSString ─────────────────────────────────────────

// ── Category: NSStringDrawing on NSString ─────────────────────────────────────────

fun NSString.sizeWithAttributes(attrs: MemorySegment): CGSize {
    val sel = ObjCRuntime.sel("sizeWithAttributes:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, attrs) as CGSize
}

fun NSString.drawAtPoint_withAttributes(point: CGPoint, attrs: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawAtPoint:withAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, point, attrs)
}

fun NSString.drawInRect_withAttributes(rect: CGRect, attrs: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawInRect:withAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, attrs)
}

// ── Category: NSExtendedStringDrawing on NSString ─────────────────────────────────────────

fun NSString.drawWithRect_options_attributes_context(rect: CGRect, options: NSStringDrawingOptions, attributes: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawWithRect:options:attributes:context:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, options, attributes, context)
}

fun NSString.boundingRectWithSize_options_attributes_context(size: CGSize, options: NSStringDrawingOptions, attributes: MemorySegment, context: MemorySegment): CGRect {
    val sel = ObjCRuntime.sel("boundingRectWithSize:options:attributes:context:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, size, options, attributes, context) as CGRect
}

// ── Category: NSStringDrawingDeprecated on NSString ─────────────────────────────────────────

fun NSString.drawWithRect_options_attributes(rect: NSRect, options: NSStringDrawingOptions, attributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawWithRect:options:attributes:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, options, attributes)
}

fun NSString.boundingRectWithSize_options_attributes(size: NSSize, options: NSStringDrawingOptions, attributes: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("boundingRectWithSize:options:attributes:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, size, options, attributes) as NSRect
}

