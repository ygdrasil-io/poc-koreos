package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSScrubberDataSource
 * Inherits protocols: NSObject
 */
interface NSScrubberDataSource : NSObject {
    fun numberOfItemsForScrubber(scrubber: MemorySegment): NSInteger
    
    fun scrubber_viewForItemAtIndex(scrubber: MemorySegment, index: NSInteger): MemorySegment
    
}

