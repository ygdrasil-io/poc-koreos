/**
 * Kotlin/JVM interface for Objective-C protocol: NSSplitViewDelegate
 * Inherits protocols: NSObject
 */
interface NSSplitViewDelegate : NSObject {
    // @optional
    fun splitView_canCollapseSubview(splitView: MemorySegment, subview: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:canCollapseSubview:' not implemented")
    
    // @optional
    fun splitView_shouldCollapseSubview_forDoubleClickOnDividerAtIndex(splitView: MemorySegment, subview: MemorySegment, dividerIndex: NSInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:shouldCollapseSubview:forDoubleClickOnDividerAtIndex:' not implemented")
    
    // @optional
    fun splitView_constrainMinCoordinate_ofSubviewAt(splitView: MemorySegment, proposedMinimumPosition: CGFloat, dividerIndex: NSInteger): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:constrainMinCoordinate:ofSubviewAt:' not implemented")
    
    // @optional
    fun splitView_constrainMaxCoordinate_ofSubviewAt(splitView: MemorySegment, proposedMaximumPosition: CGFloat, dividerIndex: NSInteger): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:constrainMaxCoordinate:ofSubviewAt:' not implemented")
    
    // @optional
    fun splitView_constrainSplitPosition_ofSubviewAt(splitView: MemorySegment, proposedPosition: CGFloat, dividerIndex: NSInteger): CGFloat =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:constrainSplitPosition:ofSubviewAt:' not implemented")
    
    // @optional
    fun splitView_resizeSubviewsWithOldSize(splitView: MemorySegment, oldSize: NSSize): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:resizeSubviewsWithOldSize:' not implemented")
    
    // @optional
    fun splitView_shouldAdjustSizeOfSubview(splitView: MemorySegment, view: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:shouldAdjustSizeOfSubview:' not implemented")
    
    // @optional
    fun splitView_shouldHideDividerAtIndex(splitView: MemorySegment, dividerIndex: NSInteger): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:shouldHideDividerAtIndex:' not implemented")
    
    // @optional
    fun splitView_effectiveRect_forDrawnRect_ofDividerAtIndex(splitView: MemorySegment, proposedEffectiveRect: NSRect, drawnRect: NSRect, dividerIndex: NSInteger): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:effectiveRect:forDrawnRect:ofDividerAtIndex:' not implemented")
    
    // @optional
    fun splitView_additionalEffectiveRectOfDividerAtIndex(splitView: MemorySegment, dividerIndex: NSInteger): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'splitView:additionalEffectiveRectOfDividerAtIndex:' not implemented")
    
    // @optional
    fun splitViewWillResizeSubviews(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'splitViewWillResizeSubviews:' not implemented")
    
    // @optional
    fun splitViewDidResizeSubviews(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'splitViewDidResizeSubviews:' not implemented")
    
}

