/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityStaticText
 * Inherits protocols: NSAccessibilityElement
 */
interface NSAccessibilityStaticText : NSAccessibilityElement {
    fun accessibilityValue(): MemorySegment
    
    // @optional
    fun accessibilityAttributedStringForRange(range: NSRange): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityAttributedStringForRange:' not implemented")
    
    // @optional
    fun accessibilityVisibleCharacterRange(): NSRange =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityVisibleCharacterRange' not implemented")
    
}

