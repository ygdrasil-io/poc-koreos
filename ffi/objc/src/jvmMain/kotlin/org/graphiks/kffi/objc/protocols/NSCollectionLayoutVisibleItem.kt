/**
 * Kotlin/JVM interface for Objective-C protocol: NSCollectionLayoutVisibleItem
 * Inherits protocols: NSObject
 */
interface NSCollectionLayoutVisibleItem : NSObject {
    fun alpha(): CGFloat
    
    fun setAlpha(alpha: CGFloat)
    
    fun zIndex(): NSInteger
    
    fun setZIndex(zIndex: NSInteger)
    
    fun isHidden(): BOOL
    
    fun setHidden(hidden: BOOL)
    
    fun center(): NSPoint
    
    fun setCenter(center: NSPoint)
    
    fun name(): MemorySegment
    
    fun indexPath(): MemorySegment
    
    fun frame(): NSRect
    
    fun bounds(): NSRect
    
    fun representedElementCategory(): NSCollectionElementCategory
    
    fun representedElementKind(): MemorySegment
    
    // @property alpha
    fun alpha(): CGFloat
    fun setAlpha(value: CGFloat)
    
    // @property zIndex
    fun zIndex(): NSInteger
    fun setZIndex(value: NSInteger)
    
    // @property hidden
    fun isHidden(): BOOL
    fun setHidden(value: BOOL)
    
    // @property center
    fun center(): NSPoint
    fun setCenter(value: NSPoint)
    
    // @property name
    fun name(): MemorySegment
    
    // @property indexPath
    fun indexPath(): MemorySegment
    
    // @property frame
    fun frame(): NSRect
    
    // @property bounds
    fun bounds(): NSRect
    
    // @property representedElementCategory
    fun representedElementCategory(): NSCollectionElementCategory
    
    // @property representedElementKind
    fun representedElementKind(): MemorySegment
    
}

