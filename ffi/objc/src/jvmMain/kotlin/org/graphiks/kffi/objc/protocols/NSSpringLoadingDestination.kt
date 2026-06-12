package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSSpringLoadingDestination
 * Inherits protocols: NSObject
 */
interface NSSpringLoadingDestination : NSObject {
    fun springLoadingActivated_draggingInfo(activated: BOOL, draggingInfo: MemorySegment)
    
    fun springLoadingHighlightChanged(draggingInfo: MemorySegment)
    
    // @optional
    fun springLoadingEntered(draggingInfo: MemorySegment): NSSpringLoadingOptions =
        throw UnsupportedOperationException("Optional ObjC method 'springLoadingEntered:' not implemented")
    
    // @optional
    fun springLoadingUpdated(draggingInfo: MemorySegment): NSSpringLoadingOptions =
        throw UnsupportedOperationException("Optional ObjC method 'springLoadingUpdated:' not implemented")
    
    // @optional
    fun springLoadingExited(draggingInfo: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'springLoadingExited:' not implemented")
    
    // @optional
    fun draggingEnded(draggingInfo: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'draggingEnded:' not implemented")
    
}

