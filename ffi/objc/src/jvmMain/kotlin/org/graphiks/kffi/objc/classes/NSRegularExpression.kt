package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRegularExpression
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSRegularExpression(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRegularExpression") }
        
        open fun regularExpressionWithPattern_options_error(pattern: MemorySegment, options: NSRegularExpressionOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("regularExpressionWithPattern:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pattern, options, error) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun regularExpressionWithPattern_options_error(pattern: String, options: NSRegularExpressionOptions, error: MemorySegment): MemorySegment = regularExpressionWithPattern_options_error(ObjCRuntime.newNSString(Arena.global(), pattern), options, error)
        
        open fun escapedPatternForString(string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("escapedPatternForString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun escapedPatternForStringAsString(string: MemorySegment): String = ObjCRuntime.toJavaString(escapedPatternForString(string))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun escapedPatternForString(string: String): MemorySegment = escapedPatternForString(ObjCRuntime.newNSString(Arena.global(), string))
        
        /** Convenience overload — [String] parameters and [String] return type. */
        open fun escapedPatternForStringAsString(string: String): String = ObjCRuntime.toJavaString(escapedPatternForString(ObjCRuntime.newNSString(Arena.global(), string)))
        
    }
    
    open fun initWithPattern_options_error(pattern: MemorySegment, options: NSRegularExpressionOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPattern:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pattern, options, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithPattern_options_error(pattern: String, options: NSRegularExpressionOptions, error: MemorySegment): MemorySegment = initWithPattern_options_error(ObjCRuntime.newNSString(Arena.global(), pattern), options, error)
    
    // @property pattern
    open fun pattern(): MemorySegment {
        val sel = ObjCRuntime.sel("pattern")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun patternAsString(): String = ObjCRuntime.toJavaString(pattern())
    
    // @property options
    open fun options(): NSRegularExpressionOptions {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRegularExpressionOptions
    }
    
    // @property numberOfCaptureGroups
    open fun numberOfCaptureGroups(): NSUInteger {
        val sel = ObjCRuntime.sel("numberOfCaptureGroups")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _pattern: MemorySegment
    // ivar: _options: NSUInteger
    // ivar: _internal: MemorySegment
    // ivar: _checkout: int32_t
}

// ── Category: NSMatching on NSRegularExpression ─────────────────────────────────────────

fun NSRegularExpression.enumerateMatchesInString_options_range_usingBlock(string: MemorySegment, options: NSMatchingOptions, range: NSRange, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateMatchesInString:options:range:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, string, options, range, block)
}

/** @return NSArray<NSTextCheckingResult *> * */
fun NSRegularExpression.matchesInString_options_range(string: MemorySegment, options: NSMatchingOptions, range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("matchesInString:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, options, range) as MemorySegment
}

fun NSRegularExpression.numberOfMatchesInString_options_range(string: MemorySegment, options: NSMatchingOptions, range: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("numberOfMatchesInString:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, string, options, range) as NSUInteger
}

fun NSRegularExpression.firstMatchInString_options_range(string: MemorySegment, options: NSMatchingOptions, range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("firstMatchInString:options:range:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, options, range) as MemorySegment
}

fun NSRegularExpression.rangeOfFirstMatchInString_options_range(string: MemorySegment, options: NSMatchingOptions, range: NSRange): NSRange {
    val sel = ObjCRuntime.sel("rangeOfFirstMatchInString:options:range:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, string, options, range) as NSRange
}

// ── Category: NSReplacement on NSRegularExpression ─────────────────────────────────────────

fun NSRegularExpression.stringByReplacingMatchesInString_options_range_withTemplate(string: MemorySegment, options: NSMatchingOptions, range: NSRange, templ: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringByReplacingMatchesInString:options:range:withTemplate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, options, range, templ) as MemorySegment
}

fun NSRegularExpression.replaceMatchesInString_options_range_withTemplate(string: MemorySegment, options: NSMatchingOptions, range: NSRange, templ: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("replaceMatchesInString:options:range:withTemplate:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, string, options, range, templ) as NSUInteger
}

fun NSRegularExpression.replacementStringForResult_inString_offset_template(result: MemorySegment, string: MemorySegment, offset: NSInteger, templ: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("replacementStringForResult:inString:offset:template:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, result, string, offset, templ) as MemorySegment
}

// Class<*> method: +[NSRegularExpression escapedTemplateForString:]
fun NSRegularExpression_escapedTemplateForString(string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("escapedTemplateForString:")
    val cls = ObjCRuntime.getClass("NSRegularExpression")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string) as MemorySegment
}

