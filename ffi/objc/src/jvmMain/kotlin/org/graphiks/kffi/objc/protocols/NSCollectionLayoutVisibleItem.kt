package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSCollectionLayoutVisibleItem
 * Inherits protocols: NSObject
 */
interface NSCollectionLayoutVisibleItem : NSObject {
    fun alpha(): CGFloat
    
    fun setAlpha(alpha: CGFloat)
    
    fun zIndex(): NSInteger
    
    fun setZIndex(zIndex: NSInteger)
    
    fun isHidden(): BOOL
    
    fun setHidden(hidden: BOOL)
    
    fun center(): NSPoint
    
    fun setCenter(center: NSPoint)
    
    fun name(): MemorySegment
    
    fun indexPath(): MemorySegment
    
    fun frame(): NSRect
    
    fun bounds(): NSRect
    
    fun representedElementCategory(): NSCollectionElementCategory
    
    fun representedElementKind(): MemorySegment
    
    // @property alpha
    // @property zIndex
    // @property hidden
    // @property center
    // @property name
    // @property indexPath
    // @property frame
    // @property bounds
    // @property representedElementCategory
    // @property representedElementKind