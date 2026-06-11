/**
 * Kotlin/JVM wrapper for Objective-C class: NSFont
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSFont(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFont") }
        
        fun fontWithName_size(fontName: MemorySegment, fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fontWithName:size:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontName, fontSize) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fontWithName_size(fontName: String, fontSize: CGFloat): MemorySegment = fontWithName_size(ObjCRuntime.newNSString(Arena.global(), fontName), fontSize)
        
        fun fontWithName_matrix(fontName: MemorySegment, fontMatrix: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontWithName:matrix:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontName, fontMatrix) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fontWithName_matrix(fontName: String, fontMatrix: MemorySegment): MemorySegment = fontWithName_matrix(ObjCRuntime.newNSString(Arena.global(), fontName), fontMatrix)
        
        fun fontWithDescriptor_size(fontDescriptor: MemorySegment, fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fontWithDescriptor:size:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontDescriptor, fontSize) as MemorySegment
        }
        
        fun fontWithDescriptor_textTransform(fontDescriptor: MemorySegment, textTransform: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontWithDescriptor:textTransform:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontDescriptor, textTransform) as MemorySegment
        }
        
        fun userFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("userFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun userFixedPitchFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("userFixedPitchFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun setUserFont(font: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setUserFont:")
            ObjCRuntime.msgSend(null, _class, sel, font)
        }
        
        fun setUserFixedPitchFont(font: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setUserFixedPitchFont:")
            ObjCRuntime.msgSend(null, _class, sel, font)
        }
        
        fun systemFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("systemFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun boldSystemFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("boldSystemFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun labelFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("labelFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun titleBarFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("titleBarFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun menuFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("menuFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun menuBarFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("menuBarFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun messageFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("messageFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun paletteFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("paletteFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun toolTipsFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("toolTipsFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun controlContentFontOfSize(fontSize: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("controlContentFontOfSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize) as MemorySegment
        }
        
        fun systemFontOfSize_weight(fontSize: CGFloat, weight: NSFontWeight): MemorySegment {
            val sel = ObjCRuntime.sel("systemFontOfSize:weight:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize, weight) as MemorySegment
        }
        
        fun monospacedDigitSystemFontOfSize_weight(fontSize: CGFloat, weight: NSFontWeight): MemorySegment {
            val sel = ObjCRuntime.sel("monospacedDigitSystemFontOfSize:weight:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize, weight) as MemorySegment
        }
        
        fun systemFontOfSize_weight_width(fontSize: CGFloat, weight: NSFontWeight, width: NSFontWidth): MemorySegment {
            val sel = ObjCRuntime.sel("systemFontOfSize:weight:width:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize, weight, width) as MemorySegment
        }
        
        fun monospacedSystemFontOfSize_weight(fontSize: CGFloat, weight: NSFontWeight): MemorySegment {
            val sel = ObjCRuntime.sel("monospacedSystemFontOfSize:weight:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontSize, weight) as MemorySegment
        }
        
        fun systemFontSizeForControlSize(controlSize: NSControlSize): CGFloat {
            val sel = ObjCRuntime.sel("systemFontSizeForControlSize:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel, controlSize) as CGFloat
        }
        
        fun systemFontSize(): CGFloat {
            val sel = ObjCRuntime.sel("systemFontSize")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        fun smallSystemFontSize(): CGFloat {
            val sel = ObjCRuntime.sel("smallSystemFontSize")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        fun labelFontSize(): CGFloat {
            val sel = ObjCRuntime.sel("labelFontSize")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
    }
    
    fun fontWithSize(fontSize: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("fontWithSize:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontSize) as MemorySegment
    }
    
    fun boundingRectForCGGlyph(glyph: CGGlyph): NSRect {
        val sel = ObjCRuntime.sel("boundingRectForCGGlyph:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyph) as NSRect
    }
    
    fun advancementForCGGlyph(glyph: CGGlyph): NSSize {
        val sel = ObjCRuntime.sel("advancementForCGGlyph:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, glyph) as NSSize
    }
    
    fun getBoundingRects_forCGGlyphs_count(bounds: MemorySegment, glyphs: MemorySegment, glyphCount: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("getBoundingRects:forCGGlyphs:count:")
        ObjCRuntime.msgSend(null, ptr, sel, bounds, glyphs, glyphCount)
    }
    
    fun getAdvancements_forCGGlyphs_count(advancements: MemorySegment, glyphs: MemorySegment, glyphCount: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("getAdvancements:forCGGlyphs:count:")
        ObjCRuntime.msgSend(null, ptr, sel, advancements, glyphs, glyphCount)
    }
    
    fun `set`(): Unit {
        val sel = ObjCRuntime.sel("set")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setInContext(graphicsContext: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setInContext:")
        ObjCRuntime.msgSend(null, ptr, sel, graphicsContext)
    }
    
    // @property systemFontSize
    fun systemFontSize(): CGFloat {
        val sel = ObjCRuntime.sel("systemFontSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property smallSystemFontSize
    fun smallSystemFontSize(): CGFloat {
        val sel = ObjCRuntime.sel("smallSystemFontSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property labelFontSize
    fun labelFontSize(): CGFloat {
        val sel = ObjCRuntime.sel("labelFontSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property fontName
    fun fontName(): MemorySegment {
        val sel = ObjCRuntime.sel("fontName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun fontNameAsString(): String = ObjCRuntime.toJavaString(fontName())
    
    // @property pointSize
    fun pointSize(): CGFloat {
        val sel = ObjCRuntime.sel("pointSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property matrix
    fun matrix(): MemorySegment {
        val sel = ObjCRuntime.sel("matrix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property familyName
    fun familyName(): MemorySegment {
        val sel = ObjCRuntime.sel("familyName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun familyNameAsString(): String = ObjCRuntime.toJavaString(familyName())
    
    // @property displayName
    fun displayName(): MemorySegment {
        val sel = ObjCRuntime.sel("displayName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun displayNameAsString(): String = ObjCRuntime.toJavaString(displayName())
    
    // @property fontDescriptor
    fun fontDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textTransform
    fun textTransform(): MemorySegment {
        val sel = ObjCRuntime.sel("textTransform")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfGlyphs
    fun numberOfGlyphs(): NSUInteger {
        val sel = ObjCRuntime.sel("numberOfGlyphs")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property mostCompatibleStringEncoding
    fun mostCompatibleStringEncoding(): NSStringEncoding {
        val sel = ObjCRuntime.sel("mostCompatibleStringEncoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSStringEncoding
    }
    
    // @property coveredCharacterSet
    fun coveredCharacterSet(): MemorySegment {
        val sel = ObjCRuntime.sel("coveredCharacterSet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property boundingRectForFont
    fun boundingRectForFont(): NSRect {
        val sel = ObjCRuntime.sel("boundingRectForFont")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property maximumAdvancement
    fun maximumAdvancement(): NSSize {
        val sel = ObjCRuntime.sel("maximumAdvancement")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property ascender
    fun ascender(): CGFloat {
        val sel = ObjCRuntime.sel("ascender")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property descender
    fun descender(): CGFloat {
        val sel = ObjCRuntime.sel("descender")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property leading
    fun leading(): CGFloat {
        val sel = ObjCRuntime.sel("leading")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property underlinePosition
    fun underlinePosition(): CGFloat {
        val sel = ObjCRuntime.sel("underlinePosition")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property underlineThickness
    fun underlineThickness(): CGFloat {
        val sel = ObjCRuntime.sel("underlineThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property italicAngle
    fun italicAngle(): CGFloat {
        val sel = ObjCRuntime.sel("italicAngle")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property capHeight
    fun capHeight(): CGFloat {
        val sel = ObjCRuntime.sel("capHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property xHeight
    fun xHeight(): CGFloat {
        val sel = ObjCRuntime.sel("xHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property fixedPitch
    fun isFixedPitch(): BOOL {
        val sel = ObjCRuntime.sel("isFixedPitch")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property verticalFont
    fun verticalFont(): MemorySegment {
        val sel = ObjCRuntime.sel("verticalFont")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property vertical
    fun isVertical(): BOOL {
        val sel = ObjCRuntime.sel("isVertical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSFont_Deprecated on NSFont ─────────────────────────────────────────

fun NSFont.glyphWithName(name: MemorySegment): NSGlyph {
    val sel = ObjCRuntime.sel("glyphWithName:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, name) as NSGlyph
}

fun NSFont.boundingRectForGlyph(glyph: NSGlyph): NSRect {
    val sel = ObjCRuntime.sel("boundingRectForGlyph:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, glyph) as NSRect
}

fun NSFont.advancementForGlyph(glyph: NSGlyph): NSSize {
    val sel = ObjCRuntime.sel("advancementForGlyph:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, glyph) as NSSize
}

fun NSFont.getBoundingRects_forGlyphs_count(bounds: MemorySegment, glyphs: MemorySegment, glyphCount: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("getBoundingRects:forGlyphs:count:")
    ObjCRuntime.msgSend(null, ptr, sel, bounds, glyphs, glyphCount)
}

fun NSFont.getAdvancements_forGlyphs_count(advancements: MemorySegment, glyphs: MemorySegment, glyphCount: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("getAdvancements:forGlyphs:count:")
    ObjCRuntime.msgSend(null, ptr, sel, advancements, glyphs, glyphCount)
}

fun NSFont.getAdvancements_forPackedGlyphs_length(advancements: MemorySegment, packedGlyphs: MemorySegment, length: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("getAdvancements:forPackedGlyphs:length:")
    ObjCRuntime.msgSend(null, ptr, sel, advancements, packedGlyphs, length)
}

fun NSFont.screenFontWithRenderingMode(renderingMode: NSFontRenderingMode): MemorySegment {
    val sel = ObjCRuntime.sel("screenFontWithRenderingMode:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, renderingMode) as MemorySegment
}

fun NSFont.printerFont(): MemorySegment {
    val sel = ObjCRuntime.sel("printerFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSFont.screenFont(): MemorySegment {
    val sel = ObjCRuntime.sel("screenFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSFont.renderingMode(): NSFontRenderingMode {
    val sel = ObjCRuntime.sel("renderingMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFontRenderingMode
}

// @property printerFont
fun NSFont.printerFont(): MemorySegment {
    val sel = ObjCRuntime.sel("printerFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property screenFont
fun NSFont.screenFont(): MemorySegment {
    val sel = ObjCRuntime.sel("screenFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property renderingMode
fun NSFont.renderingMode(): NSFontRenderingMode {
    val sel = ObjCRuntime.sel("renderingMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFontRenderingMode
}

// ── Category: NSFont_TextStyles on NSFont ─────────────────────────────────────────

// Class method: +[NSFont preferredFontForTextStyle:options:]
fun NSFont_preferredFontForTextStyle_options(style: NSFontTextStyle, options: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("preferredFontForTextStyle:options:")
    val cls = ObjCRuntime.getClass("NSFont")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, style, options) as MemorySegment
}

