/**
 * Kotlin/JVM interface for Objective-C protocol: NSUserInterfaceItemSearching
 * Inherits protocols: NSObject
 */
interface NSUserInterfaceItemSearching : NSObject {
    fun searchForItemsWithSearchString_resultLimit_matchedItemHandler(searchString: MemorySegment, resultLimit: NSInteger, handleMatchedItems: MemorySegment)
    
    /** @return NSArray<NSString *> * */
    fun localizedTitlesForItem(item: MemorySegment): MemorySegment
    
    // @optional
    fun performActionForItem(item: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'performActionForItem:' not implemented")
    
    // @optional
    fun showAllHelpTopicsForSearchString(searchString: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'showAllHelpTopicsForSearchString:' not implemented")
    
}

