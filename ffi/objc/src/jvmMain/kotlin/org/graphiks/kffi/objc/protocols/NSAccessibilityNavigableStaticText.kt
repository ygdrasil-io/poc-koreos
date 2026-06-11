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

