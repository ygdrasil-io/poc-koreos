package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScanner
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSScanner(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScanner") }
        
    }
    
    open fun initWithString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithString(string: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), string))
    
    // @property string
    open fun string(): MemorySegment {
        val sel = ObjCRuntime.sel("string")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringAsString(): String = ObjCRuntime.toJavaString(string())
    
    // @property scanLocation
    open fun scanLocation(): NSUInteger {
        val sel = ObjCRuntime.sel("scanLocation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    open fun setScanLocation(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setScanLocation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property charactersToBeSkipped
    open fun charactersToBeSkipped(): MemorySegment {
        val sel = ObjCRuntime.sel("charactersToBeSkipped")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCharactersToBeSkipped(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCharactersToBeSkipped:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property caseSensitive
    open fun caseSensitive(): BOOL {
        val sel = ObjCRuntime.sel("caseSensitive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setCaseSensitive(value: BOOL) {
        val sel = ObjCRuntime.sel("setCaseSensitive:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property locale
    open fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSExtendedScanner on NSScanner ─────────────────────────────────────────

fun NSScanner.scanInt(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanInt:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanInteger(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanInteger:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanLongLong(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanLongLong:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanUnsignedLongLong(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanUnsignedLongLong:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanFloat(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanFloat:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanDouble(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanDouble:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanHexInt(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanHexInt:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanHexLongLong(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanHexLongLong:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanHexFloat(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanHexFloat:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanHexDouble(result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanHexDouble:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, result) as BOOL
}

fun NSScanner.scanString_intoString(string: MemorySegment, result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanString:intoString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, result) as BOOL
}

fun NSScanner.scanCharactersFromSet_intoString(`set`: MemorySegment, result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanCharactersFromSet:intoString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `set`, result) as BOOL
}

fun NSScanner.scanUpToString_intoString(string: MemorySegment, result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanUpToString:intoString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, result) as BOOL
}

fun NSScanner.scanUpToCharactersFromSet_intoString(`set`: MemorySegment, result: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanUpToCharactersFromSet:intoString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `set`, result) as BOOL
}

fun NSScanner.isAtEnd(): BOOL {
    val sel = ObjCRuntime.sel("isAtEnd")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class<*> method: +[NSScanner scannerWithString:]
fun NSScanner_scannerWithString(string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("scannerWithString:")
    val cls = ObjCRuntime.getClass("NSScanner")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string) as MemorySegment
}

// Class<*> method: +[NSScanner localizedScannerWithString:]
fun NSScanner_localizedScannerWithString(string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedScannerWithString:")
    val cls = ObjCRuntime.getClass("NSScanner")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string) as MemorySegment
}

// @property atEnd
    val sel = ObjCRuntime.sel("isAtEnd")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSDecimalNumberScanning on NSScanner ─────────────────────────────────────────

fun NSScanner.scanDecimal(dcm: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scanDecimal:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dcm) as BOOL
}

