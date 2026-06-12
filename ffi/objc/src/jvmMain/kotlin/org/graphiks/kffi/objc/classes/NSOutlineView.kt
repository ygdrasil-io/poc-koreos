package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOutlineView
 * Superclass: NSTableView
 * Protocols: NSAccessibilityOutline
 */
open class NSOutlineView(ptr: MemorySegment) : NSTableView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOutlineView") }
        
    }
    
    fun isExpandable(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isExpandable:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    fun numberOfChildrenOfItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("numberOfChildrenOfItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    fun child_ofItem(index: NSInteger, item: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("child:ofItem:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, item) as MemorySegment
    }
    
    fun expandItem_expandChildren(item: MemorySegment, expandChildren: BOOL): Unit {
        val sel = ObjCRuntime.sel("expandItem:expandChildren:")
        ObjCRuntime.msgSend(null, ptr, sel, item, expandChildren)
    }
    
    fun expandItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("expandItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    fun collapseItem_collapseChildren(item: MemorySegment, collapseChildren: BOOL): Unit {
        val sel = ObjCRuntime.sel("collapseItem:collapseChildren:")
        ObjCRuntime.msgSend(null, ptr, sel, item, collapseChildren)
    }
    
    fun collapseItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("collapseItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    fun reloadItem_reloadChildren(item: MemorySegment, reloadChildren: BOOL): Unit {
        val sel = ObjCRuntime.sel("reloadItem:reloadChildren:")
        ObjCRuntime.msgSend(null, ptr, sel, item, reloadChildren)
    }
    
    fun reloadItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    fun parentForItem(item: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("parentForItem:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, item) as MemorySegment
    }
    
    fun childIndexForItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("childIndexForItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    fun itemAtRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun rowForItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("rowForItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    fun levelForItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("levelForItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    fun levelForRow(row: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("levelForRow:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, row) as NSInteger
    }
    
    fun isItemExpanded(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isItemExpanded:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    fun frameOfOutlineCellAtRow(row: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("frameOfOutlineCellAtRow:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, row) as NSRect
    }
    
    fun setDropItem_dropChildIndex(item: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setDropItem:dropChildIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, item, index)
    }
    
    fun shouldCollapseAutoExpandedItemsForDeposited(deposited: BOOL): BOOL {
        val sel = ObjCRuntime.sel("shouldCollapseAutoExpandedItemsForDeposited:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, deposited) as BOOL
    }
    
    fun insertItemsAtIndexes_inParent_withAnimation(indexes: MemorySegment, parent: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("insertItemsAtIndexes:inParent:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, parent, animationOptions)
    }
    
    fun removeItemsAtIndexes_inParent_withAnimation(indexes: MemorySegment, parent: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("removeItemsAtIndexes:inParent:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, parent, animationOptions)
    }
    
    fun moveItemAtIndex_inParent_toIndex_inParent(fromIndex: NSInteger, oldParent: MemorySegment, toIndex: NSInteger, newParent: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveItemAtIndex:inParent:toIndex:inParent:")
        ObjCRuntime.msgSend(null, ptr, sel, fromIndex, oldParent, toIndex, newParent)
    }
    
    override fun `insertRowsAtIndexes_withAnimation`(indexes: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("insertRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, animationOptions)
    }
    
    override fun `removeRowsAtIndexes_withAnimation`(indexes: MemorySegment, animationOptions: NSTableViewAnimationOptions): Unit {
        val sel = ObjCRuntime.sel("removeRowsAtIndexes:withAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, animationOptions)
    }
    
    override fun `moveRowAtIndex_toIndex`(oldIndex: NSInteger, newIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveRowAtIndex:toIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, oldIndex, newIndex)
    }
    
    // @property delegate
    /** @return id<NSOutlineViewDelegate> */
    override fun `delegate`(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    override fun `setDelegate`(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dataSource
    /** @return id<NSOutlineViewDataSource> */
    override fun `dataSource`(): MemorySegment {
        val sel = ObjCRuntime.sel("dataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    override fun `setDataSource`(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property outlineTableColumn
    fun outlineTableColumn(): MemorySegment {
        val sel = ObjCRuntime.sel("outlineTableColumn")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOutlineTableColumn(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOutlineTableColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indentationPerLevel
    fun indentationPerLevel(): CGFloat {
        val sel = ObjCRuntime.sel("indentationPerLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setIndentationPerLevel(value: CGFloat) {
        val sel = ObjCRuntime.sel("setIndentationPerLevel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indentationMarkerFollowsCell
    fun indentationMarkerFollowsCell(): BOOL {
        val sel = ObjCRuntime.sel("indentationMarkerFollowsCell")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIndentationMarkerFollowsCell(value: BOOL) {
        val sel = ObjCRuntime.sel("setIndentationMarkerFollowsCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoresizesOutlineColumn
    fun autoresizesOutlineColumn(): BOOL {
        val sel = ObjCRuntime.sel("autoresizesOutlineColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutoresizesOutlineColumn(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutoresizesOutlineColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosaveExpandedItems
    fun autosaveExpandedItems(): BOOL {
        val sel = ObjCRuntime.sel("autosaveExpandedItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutosaveExpandedItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutosaveExpandedItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userInterfaceLayoutDirection
    override fun `userInterfaceLayoutDirection`(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    override fun `setUserInterfaceLayoutDirection`(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property stronglyReferencesItems
    fun stronglyReferencesItems(): BOOL {
        val sel = ObjCRuntime.sel("stronglyReferencesItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setStronglyReferencesItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setStronglyReferencesItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

