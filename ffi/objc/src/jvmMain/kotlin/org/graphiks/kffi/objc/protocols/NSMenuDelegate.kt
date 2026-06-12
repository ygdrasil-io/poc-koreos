package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSMenuDelegate
 * Inherits protocols: NSObject
 */
interface NSMenuDelegate : NSObject {
    // @optional
    fun menuNeedsUpdate(menu: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'menuNeedsUpdate:' not implemented")
    
    // @optional
    fun numberOfItemsInMenu(menu: MemorySegment): NSInteger =
        throw UnsupportedOperationException("Optional ObjC method 'numberOfItemsInMenu:' not implemented")
    
    // @optional
    fun menu_updateItem_atIndex_shouldCancel(menu: MemorySegment, item: MemorySegment, index: NSInteger, shouldCancel: BOOL): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'menu:updateItem:atIndex:shouldCancel:' not implemented")
    
    // @optional
    fun menuHasKeyEquivalent_forEvent_target_action(menu: MemorySegment, event: MemorySegment, target: MemorySegment, action: MemorySegment): BOOL =
        throw UnsupportedOperationException("Optional ObjC method 'menuHasKeyEquivalent:forEvent:target:action:' not implemented")
    
    // @optional
    fun menuWillOpen(menu: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'menuWillOpen:' not implemented")
    
    // @optional
    fun menuDidClose(menu: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'menuDidClose:' not implemented")
    
    // @optional
    fun menu_willHighlightItem(menu: MemorySegment, item: MemorySegment): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'menu:willHighlightItem:' not implemented")
    
    // @optional
    fun confinementRectForMenu_onScreen(menu: MemorySegment, screen: MemorySegment): NSRect =
        throw UnsupportedOperationException("Optional ObjC method 'confinementRectForMenu:onScreen:' not implemented")
    
}

