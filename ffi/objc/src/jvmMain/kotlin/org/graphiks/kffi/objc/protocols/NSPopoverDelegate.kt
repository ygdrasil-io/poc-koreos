/**
 * Kotlin/JVM interface for Objective-C protocol: NSPopoverDelegate
 * Inherits protocols: NSObject
 */
interface NSPopoverDelegate : NSObject {
    // @optional
    fun popoverShouldClose(popover: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'popoverShouldClose:' not implemented")
    
    // @optional
    fun popoverShouldDetach(popover: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'popoverShouldDetach:' not implemented")
    
    // @optional
    fun popoverDidDetach(popover: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'popoverDidDetach:' not implemented")
    
    // @optional
    fun detachableWindowForPopover(popover: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'detachableWindowForPopover:' not implemented")
    
    // @optional
    fun popoverWillShow(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'popoverWillShow:' not implemented")
    
    // @optional
    fun popoverDidShow(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'popoverDidShow:' not implemented")
    
    // @optional
    fun popoverWillClose(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'popoverWillClose:' not implemented")
    
    // @optional
    fun popoverDidClose(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'popoverDidClose:' not implemented")
    
}

