package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSToolbarItemGroup
 * Superclass: NSToolbarItem
 */
open class NSToolbarItemGroup(ptr: MemorySegment) : NSToolbarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSToolbarItemGroup") }
        
        fun groupWithItemIdentifier_titles_selectionMode_labels_target_action(itemIdentifier: NSToolbarItemIdentifier, titles: MemorySegment, selectionMode: NSToolbarItemGroupSelectionMode, labels: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("groupWithItemIdentifier:titles:selectionMode:labels:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, itemIdentifier, titles, selectionMode, labels, target, action) as MemorySegment
        }
        
        fun groupWithItemIdentifier_images_selectionMode_labels_target_action(itemIdentifier: NSToolbarItemIdentifier, images: MemorySegment, selectionMode: NSToolbarItemGroupSelectionMode, labels: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("groupWithItemIdentifier:images:selectionMode:labels:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, itemIdentifier, images, selectionMode, labels, target, action) as MemorySegment
        }
        
    }
    
    fun setSelected_atIndex(selected: BOOL, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setSelected:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, selected, index)
    }
    
    fun isSelectedAtIndex(index: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isSelectedAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, index) as BOOL
    }
    
    // @property subitems
    /** @return NSArray<__kindof NSToolbarItem *> * */
    fun subitems(): MemorySegment {
        val sel = ObjCRuntime.sel("subitems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubitems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubitems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property controlRepresentation
    fun controlRepresentation(): NSToolbarItemGroupControlRepresentation {
        val sel = ObjCRuntime.sel("controlRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemGroupControlRepresentation
    }
    fun setControlRepresentation(value: NSToolbarItemGroupControlRepresentation) {
        val sel = ObjCRuntime.sel("setControlRepresentation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionMode
    fun selectionMode(): NSToolbarItemGroupSelectionMode {
        val sel = ObjCRuntime.sel("selectionMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemGroupSelectionMode
    }
    fun setSelectionMode(value: NSToolbarItemGroupSelectionMode) {
        val sel = ObjCRuntime.sel("setSelectionMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedIndex
    fun selectedIndex(): NSInteger {
        val sel = ObjCRuntime.sel("selectedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

