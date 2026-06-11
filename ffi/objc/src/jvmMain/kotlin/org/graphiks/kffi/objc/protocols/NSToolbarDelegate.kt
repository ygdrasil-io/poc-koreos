/**
 * Kotlin/JVM interface for Objective-C protocol: NSToolbarDelegate
 * Inherits protocols: NSObject
 */
interface NSToolbarDelegate : NSObject {
    // @optional
    fun toolbar_itemForItemIdentifier_willBeInsertedIntoToolbar(toolbar: MemorySegment, itemIdentifier: NSToolbarItemIdentifier, flag: BOOL): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'toolbar:itemForItemIdentifier:willBeInsertedIntoToolbar:' not implemented")
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    // @optional
    fun toolbarDefaultItemIdentifiers(toolbar: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarDefaultItemIdentifiers:' not implemented")
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    // @optional
    fun toolbarAllowedItemIdentifiers(toolbar: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarAllowedItemIdentifiers:' not implemented")
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    // @optional
    fun toolbarSelectableItemIdentifiers(toolbar: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarSelectableItemIdentifiers:' not implemented")
    
    /** @return NSSet<NSToolbarItemIdentifier> * */
    // @optional
    fun toolbarImmovableItemIdentifiers(toolbar: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarImmovableItemIdentifiers:' not implemented")
    
    // @optional
    fun toolbar_itemIdentifier_canBeInsertedAtIndex(toolbar: MemorySegment, itemIdentifier: NSToolbarItemIdentifier, index: NSInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'toolbar:itemIdentifier:canBeInsertedAtIndex:' not implemented")
    
    // @optional
    fun toolbarWillAddItem(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarWillAddItem:' not implemented")
    
    // @optional
    fun toolbarDidRemoveItem(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'toolbarDidRemoveItem:' not implemented")
    
}

