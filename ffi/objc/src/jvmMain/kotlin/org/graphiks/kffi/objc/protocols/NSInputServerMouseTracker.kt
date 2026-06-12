package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSInputServerMouseTracker
 */
interface NSInputServerMouseTracker {
    fun mouseDownOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment): BOOL
    
    fun mouseDraggedOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment): BOOL
    
    fun mouseUpOnCharacterIndex_atCoordinate_withModifier_client(index: NSUInteger, point: NSPoint, flags: NSUInteger, sender: MemorySegment)
    
}

