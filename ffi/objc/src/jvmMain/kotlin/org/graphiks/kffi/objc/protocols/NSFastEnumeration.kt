/**
 * Kotlin/JVM interface for Objective-C protocol: NSFastEnumeration
 */
interface NSFastEnumeration {
    fun countByEnumeratingWithState_objects_count(state: MemorySegment, buffer: MemorySegment, len: NSUInteger): NSUInteger
    
}

