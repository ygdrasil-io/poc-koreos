package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextSelectionDataSource
 * Inherits protocols: NSObject
 */
interface NSTextSelectionDataSource : NSObject {
    fun enumerateSubstringsFromLocation_options_usingBlock(location: MemorySegment, options: NSStringEnumerationOptions, block: MemorySegment)
    
    fun textRangeForSelectionGranularity_enclosingLocation(selectionGranularity: NSTextSelectionGranularity, location: MemorySegment): MemorySegment
    
    /** @return id<NSTextLocation> */
    fun locationFromLocation_withOffset(location: MemorySegment, offset: NSInteger): MemorySegment
    
    fun offsetFromLocation_toLocation(from: MemorySegment, to: MemorySegment): NSInteger
    
    fun baseWritingDirectionAtLocation(location: MemorySegment): NSTextSelectionNavigationWritingDirection
    
    fun enumerateCaretOffsetsInLineFragmentAtLocation_usingBlock(location: MemorySegment, block: MemorySegment)
    
    fun lineFragmentRangeForPoint_inContainerAtLocation(point: MemorySegment, location: MemorySegment): MemorySegment
    
    // @optional
    fun enumerateContainerBoundariesFromLocation_reverse_usingBlock(location: MemorySegment, reverse: BOOL, block: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'enumerateContainerBoundariesFromLocation:reverse:usingBlock:' not implemented")
    
    // @optional
    fun textLayoutOrientationAtLocation(location: MemorySegment): NSTextSelectionNavigationLayoutOrientation =
        throw UnsupportedOperationException("Optional ObjC method 'textLayoutOrientationAtLocation:' not implemented")
    
    fun documentRange(): MemorySegment
    
    // @property documentRange