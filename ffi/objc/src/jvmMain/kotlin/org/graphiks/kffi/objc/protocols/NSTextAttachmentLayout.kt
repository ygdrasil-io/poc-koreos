package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextAttachmentLayout
 * Inherits protocols: NSObject
 */
interface NSTextAttachmentLayout : NSObject {
    fun imageForBounds_attributes_location_textContainer(bounds: CGRect, attributes: MemorySegment, location: MemorySegment, textContainer: MemorySegment): MemorySegment
    
    fun attachmentBoundsForAttributes_location_textContainer_proposedLineFragment_position(attributes: MemorySegment, location: MemorySegment, textContainer: MemorySegment, proposedLineFragment: CGRect, position: CGPoint): CGRect
    
    fun viewProviderForParentView_location_textContainer(parentView: MemorySegment, location: MemorySegment, textContainer: MemorySegment): MemorySegment
    
}

