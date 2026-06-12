package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSViewContentSelectionInfo
 * Inherits protocols: NSObject
 */
interface NSViewContentSelectionInfo : NSObject {
    // @optional
    fun selectionAnchorRect(): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'selectionAnchorRect' not implemented")
    
    // @property selectionAnchorRect
}

