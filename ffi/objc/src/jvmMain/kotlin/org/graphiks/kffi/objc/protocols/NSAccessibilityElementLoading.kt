/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityElementLoading
 * Inherits protocols: NSObject
 */
interface NSAccessibilityElementLoading : NSObject {
    /** @return id<NSAccessibilityElement> */
    fun accessibilityElementWithToken(token: MemorySegment): MemorySegment
    
    // @optional
    fun accessibilityRangeInTargetElementWithToken(token: MemorySegment): NSRange =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityRangeInTargetElementWithToken:' not implemented")
    
}

