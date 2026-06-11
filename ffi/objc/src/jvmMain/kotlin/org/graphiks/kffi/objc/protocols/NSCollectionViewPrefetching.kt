/**
 * Kotlin/JVM interface for Objective-C protocol: NSCollectionViewPrefetching
 * Inherits protocols: NSObject
 */
interface NSCollectionViewPrefetching : NSObject {
    fun collectionView_prefetchItemsAtIndexPaths(collectionView: MemorySegment, indexPaths: MemorySegment)
    
    // @optional
    fun collectionView_cancelPrefetchingForItemsAtIndexPaths(collectionView: MemorySegment, indexPaths: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'collectionView:cancelPrefetchingForItemsAtIndexPaths:' not implemented")
    
}

