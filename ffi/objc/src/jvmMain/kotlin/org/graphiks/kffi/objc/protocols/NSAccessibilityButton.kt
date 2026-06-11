/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityButton
 * Inherits protocols: NSAccessibilityElement
 */
interface NSAccessibilityButton : NSAccessibilityElement {
    fun accessibilityLabel(): MemorySegment
    
    fun accessibilityPerformPress(): BOOL
    
}

