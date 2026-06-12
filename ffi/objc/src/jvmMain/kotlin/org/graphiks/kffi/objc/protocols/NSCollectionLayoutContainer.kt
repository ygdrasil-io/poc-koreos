package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSCollectionLayoutContainer
 * Inherits protocols: NSObject
 */
interface NSCollectionLayoutContainer : NSObject {
    fun contentSize(): NSSize
    
    fun effectiveContentSize(): NSSize
    
    fun contentInsets(): NSDirectionalEdgeInsets
    
    fun effectiveContentInsets(): NSDirectionalEdgeInsets
    
    // @property contentSize
    fun contentSize(): NSSize
    
    // @property effectiveContentSize
    fun effectiveContentSize(): NSSize
    
    // @property contentInsets
    fun contentInsets(): NSDirectionalEdgeInsets
    
    // @property effectiveContentInsets
    fun effectiveContentInsets(): NSDirectionalEdgeInsets
    
}

