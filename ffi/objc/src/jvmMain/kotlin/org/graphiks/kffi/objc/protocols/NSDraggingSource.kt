package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSDraggingSource
 * Inherits protocols: NSObject
 */
interface NSDraggingSource : NSObject {
    fun draggingSession_sourceOperationMaskForDraggingContext(session: MemorySegment, context: NSDraggingContext): NSDragOperation
    
    // @optional
    fun draggingSession_willBeginAtPoint(session: MemorySegment, screenPoint: NSPoint): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'draggingSession:willBeginAtPoint:' not implemented")
    
    // @optional
    fun draggingSession_movedToPoint(session: MemorySegment, screenPoint: NSPoint): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'draggingSession:movedToPoint:' not implemented")
    
    // @optional
    fun draggingSession_endedAtPoint_operation(session: MemorySegment, screenPoint: NSPoint, operation: NSDragOperation): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'draggingSession:endedAtPoint:operation:' not implemented")
    
    // @optional
    fun ignoreModifierKeysForDraggingSession(session: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'ignoreModifierKeysForDraggingSession:' not implemented")
    
}

