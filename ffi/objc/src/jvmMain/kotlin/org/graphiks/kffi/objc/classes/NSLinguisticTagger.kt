package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSLinguisticTagger
 * Superclass: NSObject
 */
open class NSLinguisticTagger(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLinguisticTagger") }
        
        /** @return NSArray<NSLinguisticTagScheme> * */
        open fun availableTagSchemesForUnit_language(unit: NSLinguisticTaggerUnit, language: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("availableTagSchemesForUnit:language:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, unit, language) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun availableTagSchemesForUnit_language(unit: NSLinguisticTaggerUnit, language: String): MemorySegment = availableTagSchemesForUnit_language(unit, ObjCRuntime.newNSString(Arena.global(), language))
        
        /** @return NSArray<NSLinguisticTagScheme> * */
        open fun availableTagSchemesForLanguage(language: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("availableTagSchemesForLanguage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, language) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun availableTagSchemesForLanguage(language: String): MemorySegment = availableTagSchemesForLanguage(ObjCRuntime.newNSString(Arena.global(), language))
        
        open fun dominantLanguageForString(string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dominantLanguageForString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun dominantLanguageForStringAsString(string: MemorySegment): String = ObjCRuntime.toJavaString(dominantLanguageForString(string))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun dominantLanguageForString(string: String): MemorySegment = dominantLanguageForString(ObjCRuntime.newNSString(Arena.global(), string))
        
        /** Convenience overload — [String] parameters and [String] return type. */
        open fun dominantLanguageForStringAsString(string: String): String = ObjCRuntime.toJavaString(dominantLanguageForString(ObjCRuntime.newNSString(Arena.global(), string)))
        
        open fun tagForString_atIndex_unit_scheme_orthography_tokenRange(string: MemorySegment, charIndex: NSUInteger, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, orthography: MemorySegment, tokenRange: MemorySegment): NSLinguisticTag {
            val sel = ObjCRuntime.sel("tagForString:atIndex:unit:scheme:orthography:tokenRange:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string, charIndex, unit, scheme, orthography, tokenRange) as NSLinguisticTag
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun tagForString_atIndex_unit_scheme_orthography_tokenRange(string: String, charIndex: NSUInteger, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, orthography: MemorySegment, tokenRange: MemorySegment): NSLinguisticTag = tagForString_atIndex_unit_scheme_orthography_tokenRange(ObjCRuntime.newNSString(Arena.global(), string), charIndex, unit, scheme, orthography, tokenRange)
        
        /** @return NSArray<NSLinguisticTag> * */
        open fun tagsForString_range_unit_scheme_options_orthography_tokenRanges(string: MemorySegment, range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, tokenRanges: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tagsForString:range:unit:scheme:options:orthography:tokenRanges:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), unit, scheme, options, orthography, tokenRanges) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun tagsForString_range_unit_scheme_options_orthography_tokenRanges(string: String, range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, tokenRanges: MemorySegment): MemorySegment = tagsForString_range_unit_scheme_options_orthography_tokenRanges(ObjCRuntime.newNSString(Arena.global(), string), range, unit, scheme, options, orthography, tokenRanges)
        
        open fun enumerateTagsForString_range_unit_scheme_options_orthography_usingBlock(string: MemorySegment, range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, block: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("enumerateTagsForString:range:unit:scheme:options:orthography:usingBlock:")
            ObjCRuntime.msgSend(null, _class, sel, string, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), unit, scheme, options, orthography, block)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun enumerateTagsForString_range_unit_scheme_options_orthography_usingBlock(string: String, range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, orthography: MemorySegment, block: MemorySegment): Unit = enumerateTagsForString_range_unit_scheme_options_orthography_usingBlock(ObjCRuntime.newNSString(Arena.global(), string), range, unit, scheme, options, orthography, block)
        
    }
    
    open fun initWithTagSchemes_options(tagSchemes: MemorySegment, opts: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTagSchemes:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tagSchemes, opts) as MemorySegment
    }
    
    open fun setOrthography_range(orthography: MemorySegment, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setOrthography:range:")
        ObjCRuntime.msgSend(null, ptr, sel, orthography, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    open fun orthographyAtIndex_effectiveRange(charIndex: NSUInteger, effectiveRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("orthographyAtIndex:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex, effectiveRange) as MemorySegment
    }
    
    open fun stringEditedInRange_changeInLength(newRange: NSRange, delta: NSInteger): Unit {
        val sel = ObjCRuntime.sel("stringEditedInRange:changeInLength:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), delta)
    }
    
    open fun tokenRangeAtIndex_unit(charIndex: NSUInteger, unit: NSLinguisticTaggerUnit): NSRange {
        val sel = ObjCRuntime.sel("tokenRangeAtIndex:unit:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, charIndex, unit) as NSRange
    }
    
    open fun sentenceRangeForRange(range: NSRange): NSRange {
        val sel = ObjCRuntime.sel("sentenceRangeForRange:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as NSRange
    }
    
    open fun enumerateTagsInRange_unit_scheme_options_usingBlock(range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateTagsInRange:unit:scheme:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), unit, scheme, options, block)
    }
    
    open fun tagAtIndex_unit_scheme_tokenRange(charIndex: NSUInteger, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, tokenRange: MemorySegment): NSLinguisticTag {
        val sel = ObjCRuntime.sel("tagAtIndex:unit:scheme:tokenRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex, unit, scheme, tokenRange) as NSLinguisticTag
    }
    
    /** @return NSArray<NSLinguisticTag> * */
    open fun tagsInRange_unit_scheme_options_tokenRanges(range: NSRange, unit: NSLinguisticTaggerUnit, scheme: NSLinguisticTagScheme, options: NSLinguisticTaggerOptions, tokenRanges: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("tagsInRange:unit:scheme:options:tokenRanges:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), unit, scheme, options, tokenRanges) as MemorySegment
    }
    
    open fun enumerateTagsInRange_scheme_options_usingBlock(range: NSRange, tagScheme: NSLinguisticTagScheme, opts: NSLinguisticTaggerOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateTagsInRange:scheme:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), tagScheme, opts, block)
    }
    
    open fun tagAtIndex_scheme_tokenRange_sentenceRange(charIndex: NSUInteger, scheme: NSLinguisticTagScheme, tokenRange: MemorySegment, sentenceRange: MemorySegment): NSLinguisticTag {
        val sel = ObjCRuntime.sel("tagAtIndex:scheme:tokenRange:sentenceRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex, scheme, tokenRange, sentenceRange) as NSLinguisticTag
    }
    
    /** @return NSArray<NSString *> * */
    open fun tagsInRange_scheme_options_tokenRanges(range: NSRange, tagScheme: MemorySegment, opts: NSLinguisticTaggerOptions, tokenRanges: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("tagsInRange:scheme:options:tokenRanges:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), tagScheme, opts, tokenRanges) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun tagsInRange_scheme_options_tokenRanges(range: NSRange, tagScheme: String, opts: NSLinguisticTaggerOptions, tokenRanges: MemorySegment): MemorySegment = tagsInRange_scheme_options_tokenRanges(range, ObjCRuntime.newNSString(Arena.global(), tagScheme), opts, tokenRanges)
    
    /** @return NSArray<NSString *> * */
    open fun possibleTagsAtIndex_scheme_tokenRange_sentenceRange_scores(charIndex: NSUInteger, tagScheme: MemorySegment, tokenRange: MemorySegment, sentenceRange: MemorySegment, scores: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("possibleTagsAtIndex:scheme:tokenRange:sentenceRange:scores:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charIndex, tagScheme, tokenRange, sentenceRange, scores) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun possibleTagsAtIndex_scheme_tokenRange_sentenceRange_scores(charIndex: NSUInteger, tagScheme: String, tokenRange: MemorySegment, sentenceRange: MemorySegment, scores: MemorySegment): MemorySegment = possibleTagsAtIndex_scheme_tokenRange_sentenceRange_scores(charIndex, ObjCRuntime.newNSString(Arena.global(), tagScheme), tokenRange, sentenceRange, scores)
    
    // @property tagSchemes
    /** @return NSArray<NSLinguisticTagScheme> * */
    open fun tagSchemes(): MemorySegment {
        val sel = ObjCRuntime.sel("tagSchemes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property string
    open fun string(): MemorySegment {
        val sel = ObjCRuntime.sel("string")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringAsString(): String = ObjCRuntime.toJavaString(string())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setString(value: String) = setString(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property dominantLanguage
    open fun dominantLanguage(): MemorySegment {
        val sel = ObjCRuntime.sel("dominantLanguage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun dominantLanguageAsString(): String = ObjCRuntime.toJavaString(dominantLanguage())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _schemes: MemorySegment
    // ivar: _options: NSUInteger
    // ivar: _string: MemorySegment
    // ivar: _orthographyArray: MemorySegment
    // ivar: _tokenArray: MemorySegment
    // ivar: _reserved: MemorySegment
}

