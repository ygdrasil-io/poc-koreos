/**
 * Kotlin/JVM interface for Objective-C protocol: NSUserInterfaceCompression
 */
interface NSUserInterfaceCompression {
    fun compressWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment)
    
    fun minimumSizeWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment): NSSize
    
    fun activeCompressionOptions(): MemorySegment
    
    // @property activeCompressionOptions
    fun activeCompressionOptions(): MemorySegment
    
}

