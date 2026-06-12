package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextViewportLayoutControllerDelegate
 * Inherits protocols: NSObject
 */
interface NSTextViewportLayoutControllerDelegate : NSObject {
    fun viewportBoundsForTextViewportLayoutController(textViewportLayoutController: MemorySegment): MemorySegment
    
    fun textViewportLayoutController_configureRenderingSurfaceForTextLayoutFragment(textViewportLayoutController: MemorySegment, textLayoutFragment: MemorySegment)
    
    // @optional
    fun textViewportLayoutControllerWillLayout(textViewportLayoutController: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'textViewportLayoutControllerWillLayout:' not implemented")
    
    // @optional
    fun textViewportLayoutControllerDidLayout(textViewportLayoutController: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'textViewportLayoutControllerDidLayout:' not implemented")
    
}

