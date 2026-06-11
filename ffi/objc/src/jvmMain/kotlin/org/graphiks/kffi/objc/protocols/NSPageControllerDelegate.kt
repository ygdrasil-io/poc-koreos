/**
 * Kotlin/JVM interface for Objective-C protocol: NSPageControllerDelegate
 * Inherits protocols: NSObject
 */
interface NSPageControllerDelegate : NSObject {
    // @optional
    fun pageController_identifierForObject(pageController: MemorySegment, `object`: MemorySegment): NSPageControllerObjectIdentifier =
        throw UnsupportedOperationException("Optional ObjC method 'pageController:identifierForObject:' not implemented")
    
    // @optional
    fun pageController_viewControllerForIdentifier(pageController: MemorySegment, identifier: NSPageControllerObjectIdentifier): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'pageController:viewControllerForIdentifier:' not implemented")
    
    // @optional
    fun pageController_frameForObject(pageController: MemorySegment, `object`: MemorySegment): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'pageController:frameForObject:' not implemented")
    
    // @optional
    fun pageController_prepareViewController_withObject(pageController: MemorySegment, viewController: MemorySegment, `object`: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pageController:prepareViewController:withObject:' not implemented")
    
    // @optional
    fun pageController_didTransitionToObject(pageController: MemorySegment, `object`: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pageController:didTransitionToObject:' not implemented")
    
    // @optional
    fun pageControllerWillStartLiveTransition(pageController: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pageControllerWillStartLiveTransition:' not implemented")
    
    // @optional
    fun pageControllerDidEndLiveTransition(pageController: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'pageControllerDidEndLiveTransition:' not implemented")
    
}

