package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityNavigableStaticText
 * Inherits protocols: NSAccessibilityStaticText
 */
interface NSAccessibilityNavigableStaticText : NSAccessibilityStaticText {
    fun accessibilityStringForRange(range: NSRange): MemorySegment
    
    fun accessibilityLineForIndex(index: NSInteger): NSInteger
    
    fun accessibilityRangeForLine(lineNumber: NSInteger): NSRange
    
    fun accessibilityFrameForRange(range: NSRange): NSRect
    
}

