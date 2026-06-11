/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilitySwitch
 * Inherits protocols: NSAccessibilityButton
 */
interface NSAccessibilitySwitch : NSAccessibilityButton {
    fun accessibilityValue(): MemorySegment
    
    // @optional
    fun accessibilityPerformIncrement(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityPerformIncrement' not implemented")
    
    // @optional
    fun accessibilityPerformDecrement(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityPerformDecrement' not implemented")
    
}

