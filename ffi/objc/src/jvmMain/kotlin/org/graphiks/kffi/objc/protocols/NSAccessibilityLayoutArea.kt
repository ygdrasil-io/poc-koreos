/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityLayoutArea
 * Inherits protocols: NSAccessibilityGroup
 */
interface NSAccessibilityLayoutArea : NSAccessibilityGroup {
    fun accessibilityLabel(): MemorySegment
    
    fun accessibilityChildren(): MemorySegment
    
    fun accessibilitySelectedChildren(): MemorySegment
    
    fun accessibilityFocusedUIElement(): MemorySegment
    
    // @property accessibilityFocusedUIElement
    fun accessibilityFocusedUIElement(): MemorySegment
    
}

