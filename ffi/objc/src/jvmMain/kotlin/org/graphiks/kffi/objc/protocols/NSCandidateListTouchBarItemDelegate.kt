/**
 * Kotlin/JVM interface for Objective-C protocol: NSCandidateListTouchBarItemDelegate
 * Inherits protocols: NSObject
 */
interface NSCandidateListTouchBarItemDelegate : NSObject {
    // @optional
    fun candidateListTouchBarItem_beginSelectingCandidateAtIndex(anItem: MemorySegment, index: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'candidateListTouchBarItem:beginSelectingCandidateAtIndex:' not implemented")
    
    // @optional
    fun candidateListTouchBarItem_changeSelectionFromCandidateAtIndex_toIndex(anItem: MemorySegment, previousIndex: NSInteger, index: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'candidateListTouchBarItem:changeSelectionFromCandidateAtIndex:toIndex:' not implemented")
    
    // @optional
    fun candidateListTouchBarItem_endSelectingCandidateAtIndex(anItem: MemorySegment, index: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'candidateListTouchBarItem:endSelectingCandidateAtIndex:' not implemented")
    
    // @optional
    fun candidateListTouchBarItem_changedCandidateListVisibility(anItem: MemorySegment, isVisible: BOOL): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'candidateListTouchBarItem:changedCandidateListVisibility:' not implemented")
    
}

