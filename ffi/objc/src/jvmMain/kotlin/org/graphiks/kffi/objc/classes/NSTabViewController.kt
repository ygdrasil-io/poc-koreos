package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTabViewController
 * Superclass: NSViewController
 * Protocols: NSTabViewDelegate, NSToolbarDelegate
 */
open class NSTabViewController(ptr: MemorySegment) : NSViewController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTabViewController") }
        
    }
    
    fun addTabViewItem(tabViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, tabViewItem)
    }
    
    fun insertTabViewItem_atIndex(tabViewItem: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertTabViewItem:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, tabViewItem, index)
    }
    
    fun removeTabViewItem(tabViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, tabViewItem)
    }
    
    fun tabViewItemForViewController(viewController: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("tabViewItemForViewController:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, viewController) as MemorySegment
    }
    
    override fun `viewDidLoad`(): Unit {
        val sel = ObjCRuntime.sel("viewDidLoad")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun tabView_willSelectTabViewItem(tabView: MemorySegment, tabViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("tabView:willSelectTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, tabView, tabViewItem)
    }
    
    fun tabView_didSelectTabViewItem(tabView: MemorySegment, tabViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("tabView:didSelectTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, tabView, tabViewItem)
    }
    
    fun tabView_shouldSelectTabViewItem(tabView: MemorySegment, tabViewItem: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("tabView:shouldSelectTabViewItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tabView, tabViewItem) as BOOL
    }
    
    fun toolbar_itemForItemIdentifier_willBeInsertedIntoToolbar(toolbar: MemorySegment, itemIdentifier: NSToolbarItemIdentifier, flag: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("toolbar:itemForItemIdentifier:willBeInsertedIntoToolbar:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, toolbar, itemIdentifier, flag) as MemorySegment
    }
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    fun toolbarDefaultItemIdentifiers(toolbar: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("toolbarDefaultItemIdentifiers:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, toolbar) as MemorySegment
    }
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    fun toolbarAllowedItemIdentifiers(toolbar: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("toolbarAllowedItemIdentifiers:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, toolbar) as MemorySegment
    }
    
    /** @return NSArray<NSToolbarItemIdentifier> * */
    fun toolbarSelectableItemIdentifiers(toolbar: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("toolbarSelectableItemIdentifiers:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, toolbar) as MemorySegment
    }
    
    // @property tabStyle
    fun tabStyle(): NSTabViewControllerTabStyle {
        val sel = ObjCRuntime.sel("tabStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTabViewControllerTabStyle
    }
    fun setTabStyle(value: NSTabViewControllerTabStyle) {
        val sel = ObjCRuntime.sel("setTabStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabView
    fun tabView(): MemorySegment {
        val sel = ObjCRuntime.sel("tabView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTabView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTabView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property transitionOptions
    fun transitionOptions(): NSViewControllerTransitionOptions {
        val sel = ObjCRuntime.sel("transitionOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSViewControllerTransitionOptions
    }
    fun setTransitionOptions(value: NSViewControllerTransitionOptions) {
        val sel = ObjCRuntime.sel("setTransitionOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canPropagateSelectedChildViewControllerTitle
    fun canPropagateSelectedChildViewControllerTitle(): BOOL {
        val sel = ObjCRuntime.sel("canPropagateSelectedChildViewControllerTitle")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanPropagateSelectedChildViewControllerTitle(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanPropagateSelectedChildViewControllerTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabViewItems
    /** @return NSArray<__kindof NSTabViewItem *> * */
    fun tabViewItems(): MemorySegment {
        val sel = ObjCRuntime.sel("tabViewItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTabViewItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTabViewItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedTabViewItemIndex
    fun selectedTabViewItemIndex(): NSInteger {
        val sel = ObjCRuntime.sel("selectedTabViewItemIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedTabViewItemIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedTabViewItemIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

