/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityStepper
 * Inherits protocols: NSAccessibilityElement
 */
interface NSAccessibilityStepper : NSAccessibilityElement {
    fun accessibilityLabel(): MemorySegment
    
    fun accessibilityPerformIncrement(): BOOL
    
    fun accessibilityPerformDecrement(): BOOL
    
    // @optional
    fun accessibilityValue(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityValue' not implemented")
    
}

