/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityRow
 * Inherits protocols: NSAccessibilityGroup
 */
interface NSAccessibilityRow : NSAccessibilityGroup {
    fun accessibilityIndex(): NSInteger
    
    // @optional
    fun accessibilityDisclosureLevel(): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityDisclosureLevel' not implemented")
    
}

