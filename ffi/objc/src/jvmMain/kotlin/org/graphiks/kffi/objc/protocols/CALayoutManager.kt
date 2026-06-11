/**
 * Kotlin/JVM interface for Objective-C protocol: CALayoutManager
 * Inherits protocols: NSObject
 */
interface CALayoutManager : NSObject {
    // @optional
    fun preferredSizeOfLayer(layer: MemorySegment): CGSize =
        throw UnsupportedOperationException("Optional ObjC method 'preferredSizeOfLayer:' not implemented")
    
    // @optional
    fun invalidateLayoutOfLayer(layer: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'invalidateLayoutOfLayer:' not implemented")
    
    // @optional
    fun layoutSublayersOfLayer(layer: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'layoutSublayersOfLayer:' not implemented")
    
}

