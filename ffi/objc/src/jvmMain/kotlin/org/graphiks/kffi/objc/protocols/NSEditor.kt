package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSEditor
 * Inherits protocols: NSObject
 */
interface NSEditor : NSObject {
    fun discardEditing()
    
    fun commitEditing(): BOOL
    
    fun commitEditingWithDelegate_didCommitSelector_contextInfo(delegate: MemorySegment, didCommitSelector: MemorySegment, contextInfo: MemorySegment)
    
    fun commitEditingAndReturnError(error: MemorySegment): BOOL
    
}

