/**
 * Kotlin/JVM interface for Objective-C protocol: NSScrubberFlowLayoutDelegate
 * Inherits protocols: NSScrubberDelegate
 */
interface NSScrubberFlowLayoutDelegate : NSScrubberDelegate {
    // @optional
    fun scrubber_layout_sizeForItemAtIndex(scrubber: MemorySegment, layout: MemorySegment, itemIndex: NSInteger): NSSize =
        throw UnsupportedOperationException("Optional ObjC method 'scrubber:layout:sizeForItemAtIndex:' not implemented")
    
}

