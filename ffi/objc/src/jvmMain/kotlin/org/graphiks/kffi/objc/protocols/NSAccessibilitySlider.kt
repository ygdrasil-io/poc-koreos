package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibilitySlider
 * Inherits protocols: NSAccessibilityElement
 */
interface NSAccessibilitySlider : NSAccessibilityElement {
    fun accessibilityLabel(): MemorySegment
    
    fun accessibilityValue(): MemorySegment
    
    fun accessibilityPerformIncrement(): BOOL
    
    fun accessibilityPerformDecrement(): BOOL
    
}

