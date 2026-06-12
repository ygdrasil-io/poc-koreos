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
    // @property effectiveContentSize
    // @property contentInsets
    // @property effectiveContentInsets