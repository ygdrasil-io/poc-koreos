package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSToolbarItemValidation
 * Inherits protocols: NSObject
 */
interface NSToolbarItemValidation : NSObject {
    fun validateToolbarItem(item: MemorySegment): BOOL
    
}

