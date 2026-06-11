/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityContainsTransientUI
 * Inherits protocols: NSAccessibilityElement
 */
interface NSAccessibilityContainsTransientUI : NSAccessibilityElement {
    fun accessibilityPerformShowAlternateUI(): BOOL
    
    fun accessibilityPerformShowDefaultUI(): BOOL
    
    fun isAccessibilityAlternateUIVisible(): BOOL
    
}

