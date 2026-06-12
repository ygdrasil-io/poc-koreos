package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSFontChanging
 * Inherits protocols: NSObject
 */
interface NSFontChanging : NSObject {
    // @optional
    fun changeFont(sender: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'changeFont:' not implemented")
    
    // @optional
    fun validModesForFontPanel(fontPanel: MemorySegment): NSFontPanelModeMask =
        throw UnsupportedOperationException("Optional ObjC method 'validModesForFontPanel:' not implemented")
    
}

