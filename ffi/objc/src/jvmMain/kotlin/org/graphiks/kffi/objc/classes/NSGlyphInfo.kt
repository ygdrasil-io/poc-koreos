package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGlyphInfo
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSGlyphInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGlyphInfo") }
        
        open fun glyphInfoWithCGGlyph_forFont_baseString(glyph: CGGlyph, font: MemorySegment, string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("glyphInfoWithCGGlyph:forFont:baseString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, glyph, font, string) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun glyphInfoWithCGGlyph_forFont_baseString(glyph: CGGlyph, font: MemorySegment, string: String): MemorySegment = glyphInfoWithCGGlyph_forFont_baseString(glyph, font, ObjCRuntime.newNSString(Arena.global(), string))
        
    }
    
    // @property glyphID
    open fun glyphID(): CGGlyph {
        val sel = ObjCRuntime.sel("glyphID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as CGGlyph
    }
    
    // @property baseString
    open fun baseString(): MemorySegment {
        val sel = ObjCRuntime.sel("baseString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun baseStringAsString(): String = ObjCRuntime.toJavaString(baseString())
    
}

// ── Category: NSGlyphInfo_Deprecated on NSGlyphInfo ─────────────────────────────────────────

fun NSGlyphInfo.glyphName(): MemorySegment {
    val sel = ObjCRuntime.sel("glyphName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSGlyphInfo.characterIdentifier(): NSUInteger {
    val sel = ObjCRuntime.sel("characterIdentifier")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSGlyphInfo.characterCollection(): NSCharacterCollection {
    val sel = ObjCRuntime.sel("characterCollection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCharacterCollection
}

// Class<*> method: +[NSGlyphInfo glyphInfoWithGlyphName:forFont:baseString:]
fun NSGlyphInfo_glyphInfoWithGlyphName_forFont_baseString(glyphName: MemorySegment, font: MemorySegment, string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("glyphInfoWithGlyphName:forFont:baseString:")
    val cls = ObjCRuntime.getClass("NSGlyphInfo")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, glyphName, font, string) as MemorySegment
}

// Class<*> method: +[NSGlyphInfo glyphInfoWithGlyph:forFont:baseString:]
fun NSGlyphInfo_glyphInfoWithGlyph_forFont_baseString(glyph: NSGlyph, font: MemorySegment, string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("glyphInfoWithGlyph:forFont:baseString:")
    val cls = ObjCRuntime.getClass("NSGlyphInfo")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, glyph, font, string) as MemorySegment
}

// Class<*> method: +[NSGlyphInfo glyphInfoWithCharacterIdentifier:collection:baseString:]
fun NSGlyphInfo_glyphInfoWithCharacterIdentifier_collection_baseString(cid: NSUInteger, characterCollection: NSCharacterCollection, string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("glyphInfoWithCharacterIdentifier:collection:baseString:")
    val cls = ObjCRuntime.getClass("NSGlyphInfo")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, cid, characterCollection, string) as MemorySegment
}

// @property glyphName
fun NSGlyphInfo.glyphName(): MemorySegment {
    val sel = ObjCRuntime.sel("glyphName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property characterIdentifier
fun NSGlyphInfo.characterIdentifier(): NSUInteger {
    val sel = ObjCRuntime.sel("characterIdentifier")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

// @property characterCollection
fun NSGlyphInfo.characterCollection(): NSCharacterCollection {
    val sel = ObjCRuntime.sel("characterCollection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCharacterCollection
}

