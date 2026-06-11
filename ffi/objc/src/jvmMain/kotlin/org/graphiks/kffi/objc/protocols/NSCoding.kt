/**
 * Kotlin/JVM interface for Objective-C protocol: NSCoding
 */
interface NSCoding {
    fun encodeWithCoder(coder: MemorySegment)
    
    fun initWithCoder(coder: MemorySegment): MemorySegment
    
}

