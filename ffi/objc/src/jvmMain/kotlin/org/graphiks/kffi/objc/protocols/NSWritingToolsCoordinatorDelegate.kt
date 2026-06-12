package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSWritingToolsCoordinatorDelegate
 * Inherits protocols: NSObject
 */
interface NSWritingToolsCoordinatorDelegate : NSObject {
    fun writingToolsCoordinator_requestsContextsForScope_completion(writingToolsCoordinator: MemorySegment, scope: NSWritingToolsCoordinatorContextScope, completion: MemorySegment)
    
    fun writingToolsCoordinator_replaceRange_inContext_proposedText_reason_animationParameters_completion(writingToolsCoordinator: MemorySegment, range: NSRange, context: MemorySegment, replacementText: MemorySegment, reason: NSWritingToolsCoordinatorTextReplacementReason, animationParameters: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_selectRanges_inContext_completion(writingToolsCoordinator: MemorySegment, ranges: MemorySegment, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_requestsBoundingBezierPathsForRange_inContext_completion(writingToolsCoordinator: MemorySegment, range: NSRange, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_requestsUnderlinePathsForRange_inContext_completion(writingToolsCoordinator: MemorySegment, range: NSRange, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_prepareForTextAnimation_forRange_inContext_completion(writingToolsCoordinator: MemorySegment, textAnimation: NSWritingToolsCoordinatorTextAnimation, range: NSRange, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_requestsPreviewForTextAnimation_ofRange_inContext_completion(writingToolsCoordinator: MemorySegment, textAnimation: NSWritingToolsCoordinatorTextAnimation, range: NSRange, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_requestsPreviewForRect_inContext_completion(writingToolsCoordinator: MemorySegment, rect: NSRect, context: MemorySegment, completion: MemorySegment)
    
    fun writingToolsCoordinator_finishTextAnimation_forRange_inContext_completion(writingToolsCoordinator: MemorySegment, textAnimation: NSWritingToolsCoordinatorTextAnimation, range: NSRange, context: MemorySegment, completion: MemorySegment)
    
    // @optional
    fun writingToolsCoordinator_requestsSingleContainerSubrangesOfRange_inContext_completion(writingToolsCoordinator: MemorySegment, range: NSRange, context: MemorySegment, completion: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'writingToolsCoordinator:requestsSingleContainerSubrangesOfRange:inContext:completion:' not implemented")
    
    // @optional
    fun writingToolsCoordinator_requestsDecorationContainerViewForRange_inContext_completion(writingToolsCoordinator: MemorySegment, range: NSRange, context: MemorySegment, completion: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'writingToolsCoordinator:requestsDecorationContainerViewForRange:inContext:completion:' not implemented")
    
    // @optional
    fun writingToolsCoordinator_willChangeToState_completion(writingToolsCoordinator: MemorySegment, newState: NSWritingToolsCoordinatorState, completion: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'writingToolsCoordinator:willChangeToState:completion:' not implemented")
    
    // @optional
    fun writingToolsCoordinator_requestsRangeInContextWithIdentifierForPoint_completion(writingToolsCoordinator: MemorySegment, point: NSPoint, completion: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'writingToolsCoordinator:requestsRangeInContextWithIdentifierForPoint:completion:' not implemented")
    
}

