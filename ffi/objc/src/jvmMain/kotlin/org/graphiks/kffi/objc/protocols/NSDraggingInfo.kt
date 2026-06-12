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
    fun draggingDestinationWindow(): MemorySegment
    
    // @property draggingSourceOperationMask
    fun draggingSourceOperationMask(): NSDragOperation
    
    // @property draggingLocation
    fun draggingLocation(): NSPoint
    
    // @property draggedImageLocation
    fun draggedImageLocation(): NSPoint
    
    // @property draggedImage
    fun draggedImage(): MemorySegment
    
    // @property draggingPasteboard
    fun draggingPasteboard(): MemorySegment
    
    // @property draggingSource
    fun draggingSource(): MemorySegment
    
    // @property draggingSequenceNumber
    fun draggingSequenceNumber(): NSInteger
    
    // @property draggingFormation
    fun draggingFormation(): NSDraggingFormation
    fun setDraggingFormation(value: NSDraggingFormation)
    
    // @property animatesToDestination
    fun animatesToDestination(): BOOL
    fun setAnimatesToDestination(value: BOOL)
    
    // @property numberOfValidItemsForDrop
    fun numberOfValidItemsForDrop(): NSInteger
    fun setNumberOfValidItemsForDrop(value: NSInteger)
    
    // @property springLoadingHighlight
    fun springLoadingHighlight(): NSSpringLoadingHighlight
    
}

