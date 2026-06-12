package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSSeguePerforming
 * Inherits protocols: NSObject
 */
interface NSSeguePerforming : NSObject {
    // @optional
    fun prepareForSegue_sender(segue: MemorySegment, sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'prepareForSegue:sender:' not implemented")
    
    // @optional
    fun performSegueWithIdentifier_sender(identifier: NSStoryboardSegueIdentifier, sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'performSegueWithIdentifier:sender:' not implemented")
    
    // @optional
    fun shouldPerformSegueWithIdentifier_sender(identifier: NSStoryboardSegueIdentifier, sender: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'shouldPerformSegueWithIdentifier:sender:' not implemented")
    
}

