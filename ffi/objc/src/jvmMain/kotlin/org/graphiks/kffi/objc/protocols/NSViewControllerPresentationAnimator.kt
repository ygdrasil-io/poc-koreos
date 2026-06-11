/**
 * Kotlin/JVM interface for Objective-C protocol: NSViewControllerPresentationAnimator
 * Inherits protocols: NSObject
 */
interface NSViewControllerPresentationAnimator : NSObject {
    fun animatePresentationOfViewController_fromViewController(viewController: MemorySegment, fromViewController: MemorySegment)
    
    fun animateDismissalOfViewController_fromViewController(viewController: MemorySegment, fromViewController: MemorySegment)
    
}

