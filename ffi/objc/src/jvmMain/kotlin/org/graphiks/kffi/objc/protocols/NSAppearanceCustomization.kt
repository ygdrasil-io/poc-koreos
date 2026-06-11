/**
 * Kotlin/JVM interface for Objective-C protocol: NSAppearanceCustomization
 * Inherits protocols: NSObject
 */
interface NSAppearanceCustomization : NSObject {
    fun appearance(): MemorySegment
    
    fun setAppearance(appearance: MemorySegment)
    
    fun effectiveAppearance(): MemorySegment
    
    // @property appearance
    fun appearance(): MemorySegment
    fun setAppearance(value: MemorySegment)
    
    // @property effectiveAppearance
    fun effectiveAppearance(): MemorySegment
    
}

