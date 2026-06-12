package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSImageDelegate
 * Inherits protocols: NSObject
 */
interface NSImageDelegate : NSObject {
    // @optional
    fun imageDidNotDraw_inRect(sender: MemorySegment, rect: NSRect): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'imageDidNotDraw:inRect:' not implemented")
    
    // @optional
    fun image_willLoadRepresentation(image: MemorySegment, rep: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'image:willLoadRepresentation:' not implemented")
    
    // @optional
    fun image_didLoadRepresentationHeader(image: MemorySegment, rep: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'image:didLoadRepresentationHeader:' not implemented")
    
    // @optional
    fun image_didLoadPartOfRepresentation_withValidRows(image: MemorySegment, rep: MemorySegment, rows: NSInteger): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'image:didLoadPartOfRepresentation:withValidRows:' not implemented")
    
    // @optional
    fun image_didLoadRepresentation_withStatus(image: MemorySegment, rep: MemorySegment, status: NSImageLoadStatus): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'image:didLoadRepresentation:withStatus:' not implemented")
    
}

