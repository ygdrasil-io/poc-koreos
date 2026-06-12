package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextElementProvider
 * Inherits protocols: NSObject
 */
interface NSTextElementProvider : NSObject {
    /** @return id<NSTextLocation> */
    fun enumerateTextElementsFromLocation_options_usingBlock(textLocation: MemorySegment, options: NSTextContentManagerEnumerationOptions, block: MemorySegment): MemorySegment
    
    fun replaceContentsInRange_withTextElements(range: MemorySegment, textElements: MemorySegment)
    
    fun synchronizeToBackingStore(completionHandler: MemorySegment)
    
    /** @return id<NSTextLocation> */
    // @optional
    fun locationFromLocation_withOffset(location: MemorySegment, offset: NSInteger): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'locationFromLocation:withOffset:' not implemented")
    
    // @optional
    fun offsetFromLocation_toLocation(from: MemorySegment, to: MemorySegment): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'offsetFromLocation:toLocation:' not implemented")
    
    // @optional
    fun adjustedRangeFromRange_forEditingTextSelection(textRange: MemorySegment, forEditingTextSelection: BOOL): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'adjustedRangeFromRange:forEditingTextSelection:' not implemented")
    
    fun documentRange(): MemorySegment
    
    // @property documentRange