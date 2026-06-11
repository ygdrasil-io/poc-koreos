/**
 * Kotlin/JVM interface for Objective-C protocol: NSScrubberDataSource
 * Inherits protocols: NSObject
 */
interface NSScrubberDataSource : NSObject {
    fun numberOfItemsForScrubber(scrubber: MemorySegment): NSInteger
    
    fun scrubber_viewForItemAtIndex(scrubber: MemorySegment, index: NSInteger): MemorySegment
    
}

