package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextContent
 */
interface NSTextContent {
    fun contentType(): NSTextContentType
    
    fun setContentType(contentType: NSTextContentType)
    
    // @property contentType
}

