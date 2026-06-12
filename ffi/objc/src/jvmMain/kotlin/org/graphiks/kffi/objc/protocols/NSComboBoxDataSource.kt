package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSComboBoxDataSource
 * Inherits protocols: NSObject
 */
interface NSComboBoxDataSource : NSObject {
    // @optional
    fun numberOfItemsInComboBox(comboBox: MemorySegment): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'numberOfItemsInComboBox:' not implemented")
    
    // @optional
    fun comboBox_objectValueForItemAtIndex(comboBox: MemorySegment, index: NSInteger): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'comboBox:objectValueForItemAtIndex:' not implemented")
    
    // @optional
    fun comboBox_indexOfItemWithStringValue(comboBox: MemorySegment, string: MemorySegment): NSUInteger =
        throw UnsupportedOperationException("Optional ObjC method 'comboBox:indexOfItemWithStringValue:' not implemented")
    
    // @optional
    fun comboBox_completedString(comboBox: MemorySegment, string: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'comboBox:completedString:' not implemented")
    
}

