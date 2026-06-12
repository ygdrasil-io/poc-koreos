package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSGlyphStorage
 */
interface NSGlyphStorage {
    fun insertGlyphs_length_forStartingGlyphAtIndex_characterIndex(glyphs: MemorySegment, length: NSUInteger, glyphIndex: NSUInteger, charIndex: NSUInteger)
    
    fun setIntAttribute_value_forGlyphAtIndex(attributeTag: NSInteger, `val`: NSInteger, glyphIndex: NSUInteger)
    
    fun attributedString(): MemorySegment
    
    fun layoutOptions(): NSUInteger
    
}

