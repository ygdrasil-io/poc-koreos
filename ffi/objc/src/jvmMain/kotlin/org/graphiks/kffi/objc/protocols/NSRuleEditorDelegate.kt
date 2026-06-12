package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSRuleEditorDelegate
 * Inherits protocols: NSObject
 */
interface NSRuleEditorDelegate : NSObject {
    fun ruleEditor_numberOfChildrenForCriterion_withRowType(editor: MemorySegment, criterion: MemorySegment, rowType: NSRuleEditorRowType): NSInteger
    
    fun ruleEditor_child_forCriterion_withRowType(editor: MemorySegment, index: NSInteger, criterion: MemorySegment, rowType: NSRuleEditorRowType): MemorySegment
    
    fun ruleEditor_displayValueForCriterion_inRow(editor: MemorySegment, criterion: MemorySegment, row: NSInteger): MemorySegment
    
    /** @return NSDictionary<NSRuleEditorPredicatePartKey,id> * */
    // @optional
    fun ruleEditor_predicatePartsForCriterion_withDisplayValue_inRow(editor: MemorySegment, criterion: MemorySegment, value: MemorySegment, row: NSInteger): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'ruleEditor:predicatePartsForCriterion:withDisplayValue:inRow:' not implemented")
    
    // @optional
    fun ruleEditorRowsDidChange(notification: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'ruleEditorRowsDidChange:' not implemented")
    
}

