package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityElement
 * Inherits protocols: NSObject
 */
interface NSAccessibilityElement : NSObject {
    fun accessibilityFrame(): NSRect
    
    fun accessibilityParent(): MemorySegment
    
    // @optional
    fun isAccessibilityFocused(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'isAccessibilityFocused' not implemented")
    
    // @optional
    fun accessibilityIdentifier(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityIdentifier' not implemented")
    
}

