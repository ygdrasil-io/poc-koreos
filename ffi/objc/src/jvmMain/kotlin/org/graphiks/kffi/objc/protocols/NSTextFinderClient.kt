package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextFinderClient
 * Inherits protocols: NSObject
 */
interface NSTextFinderClient : NSObject {
    // @optional
    fun stringAtIndex_effectiveRange_endsWithSearchBoundary(characterIndex: NSUInteger, outRange: MemorySegment, outFlag: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'stringAtIndex:effectiveRange:endsWithSearchBoundary:' not implemented")
    
    // @optional
    fun stringLength(): NSUInteger =
        throw UnsupportedOperationException("Optional ObjC method 'stringLength' not implemented")
    
    // @optional
    fun scrollRangeToVisible(range: NSRange): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'scrollRangeToVisible:' not implemented")
    
    // @optional
    fun shouldReplaceCharactersInRanges_withStrings(ranges: MemorySegment, strings: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'shouldReplaceCharactersInRanges:withStrings:' not implemented")
    
    // @optional
    fun replaceCharactersInRange_withString(range: NSRange, string: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'replaceCharactersInRange:withString:' not implemented")
    
    // @optional
    fun didReplaceCharacters(): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'didReplaceCharacters' not implemented")
    
    // @optional
    fun contentViewAtIndex_effectiveCharacterRange(index: NSUInteger, outRange: MemorySegment): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'contentViewAtIndex:effectiveCharacterRange:' not implemented")
    
    /** @return NSArray<NSValue *> * */
    // @optional
    fun rectsForCharacterRange(range: NSRange): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'rectsForCharacterRange:' not implemented")
    
    // @optional
    fun drawCharactersInRange_forContentView(range: NSRange, view: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'drawCharactersInRange:forContentView:' not implemented")
    
    // @optional
    fun isSelectable(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'isSelectable' not implemented")
    
    // @optional
    fun allowsMultipleSelection(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'allowsMultipleSelection' not implemented")
    
    // @optional
    fun isEditable(): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'isEditable' not implemented")
    
    // @optional
    fun string(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'string' not implemented")
    
    // @optional
    fun firstSelectedRange(): NSRange =
        throw UnsupportedOperationException("Optional ObjC method 'firstSelectedRange' not implemented")
    
    /** @return NSArray<NSValue *> * */
    // @optional
    fun selectedRanges(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'selectedRanges' not implemented")
    
    // @optional
    fun setSelectedRanges(selectedRanges: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setSelectedRanges:' not implemented")
    
    /** @return NSArray<NSValue *> * */
    // @optional
    fun visibleCharacterRanges(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'visibleCharacterRanges' not implemented")
    
    // @property selectable
    // @property allowsMultipleSelection
    // @property editable
    // @property string
    // @property firstSelectedRange
    // @property selectedRanges
    /** @return NSArray<NSValue *> * */
    // @property visibleCharacterRanges
    /** @return NSArray<NSValue *> * */