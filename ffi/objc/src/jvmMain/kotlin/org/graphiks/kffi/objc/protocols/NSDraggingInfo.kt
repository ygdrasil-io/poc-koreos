package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSDraggingInfo
 * Inherits protocols: NSObject
 */
interface NSDraggingInfo : NSObject {
    fun slideDraggedImageTo(screenPoint: NSPoint)
    
    /** @return NSArray<NSString *> * */
    fun namesOfPromisedFilesDroppedAtDestination(dropDestination: MemorySegment): MemorySegment
    
    fun enumerateDraggingItemsWithOptions_forView_classes_searchOptions_usingBlock(enumOpts: NSDraggingItemEnumerationOptions, view: MemorySegment, classArray: MemorySegment, searchOptions: MemorySegment, block: MemorySegment)
    
    fun resetSpringLoading()
    
    fun draggingDestinationWindow(): MemorySegment
    
    fun draggingSourceOperationMask(): NSDragOperation
    
    fun draggingLocation(): NSPoint
    
    fun draggedImageLocation(): NSPoint
    
    fun draggedImage(): MemorySegment
    
    fun draggingPasteboard(): MemorySegment
    
    fun draggingSource(): MemorySegment
    
    fun draggingSequenceNumber(): NSInteger
    
    fun draggingFormation(): NSDraggingFormation
    
    fun setDraggingFormation(draggingFormation: NSDraggingFormation)
    
    fun animatesToDestination(): BOOL
    
    fun setAnimatesToDestination(animatesToDestination: BOOL)
    
    fun numberOfValidItemsForDrop(): NSInteger
    
    fun setNumberOfValidItemsForDrop(numberOfValidItemsForDrop: NSInteger)
    
    fun springLoadingHighlight(): NSSpringLoadingHighlight
    
    // @property draggingDestinationWindow
}

