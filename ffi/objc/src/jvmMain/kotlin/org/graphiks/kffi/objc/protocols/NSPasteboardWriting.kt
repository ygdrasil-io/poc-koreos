package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSPasteboardWriting
 * Inherits protocols: NSObject
 */
interface NSPasteboardWriting : NSObject {
    /** @return NSArray<NSPasteboardType> * */
    fun writableTypesForPasteboard(pasteboard: MemorySegment): MemorySegment
    
    // @optional
    fun writingOptionsForType_pasteboard(type: NSPasteboardType, pasteboard: MemorySegment): NSPasteboardWritingOptions =
        throw UnsupportedOperationException("Optional ObjC method 'writingOptionsForType:pasteboard:' not implemented")
    
    fun pasteboardPropertyListForType(type: NSPasteboardType): MemorySegment
    
}

