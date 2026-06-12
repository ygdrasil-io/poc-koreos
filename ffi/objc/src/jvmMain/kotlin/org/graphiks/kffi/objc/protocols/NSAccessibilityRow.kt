package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilityRow
 * Inherits protocols: NSAccessibilityGroup
 */
interface NSAccessibilityRow : NSAccessibilityGroup {
    fun accessibilityIndex(): NSInteger
    
    // @optional
    fun accessibilityDisclosureLevel(): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'accessibilityDisclosureLevel' not implemented")
    
}

