package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSComboBox
 * Superclass: NSTextField
 */
open class NSComboBox(ptr: MemorySegment) : NSTextField(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSComboBox") }
        
    }
    
    fun reloadData(): Unit {
        val sel = ObjCRuntime.sel("reloadData")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun noteNumberOfItemsChanged(): Unit {
        val sel = ObjCRuntime.sel("noteNumberOfItemsChanged")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scrollItemAtIndexToTop(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollItemAtIndexToTop:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun scrollItemAtIndexToVisible(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("scrollItemAtIndexToVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun selectItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun deselectItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("deselectItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun addItemWithObjectValue(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addItemWithObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun addItemsWithObjectValues(objects: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addItemsWithObjectValues:")
        ObjCRuntime.msgSend(null, ptr, sel, objects)
    }
    
    fun insertItemWithObjectValue_atIndex(`object`: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertItemWithObjectValue:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, index)
    }
    
    fun removeItemWithObjectValue(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeItemWithObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun removeAllItems(): Unit {
        val sel = ObjCRuntime.sel("removeAllItems")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun selectItemWithObjectValue(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectItemWithObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun itemObjectValueAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemObjectValueAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun indexOfItemWithObjectValue(`object`: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithObjectValue:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, `object`) as NSInteger
    }
    
    // @property hasVerticalScroller
    fun hasVerticalScroller(): BOOL {
        val sel = ObjCRuntime.sel("hasVerticalScroller")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasVerticalScroller(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasVerticalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property intercellSpacing
    fun intercellSpacing(): NSSize {
        val sel = ObjCRuntime.sel("intercellSpacing")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setIntercellSpacing(value: NSSize) {
        val sel = ObjCRuntime.sel("setIntercellSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property itemHeight
    fun itemHeight(): CGFloat {
        val sel = ObjCRuntime.sel("itemHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setItemHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setItemHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfVisibleItems
    fun numberOfVisibleItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfVisibleItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfVisibleItems(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfVisibleItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property buttonBordered
    fun isButtonBordered(): BOOL {
        val sel = ObjCRuntime.sel("isButtonBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setButtonBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setButtonBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesDataSource
    fun usesDataSource(): BOOL {
        val sel = ObjCRuntime.sel("usesDataSource")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesDataSource(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indexOfSelectedItem
    fun indexOfSelectedItem(): NSInteger {
        val sel = ObjCRuntime.sel("indexOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfItems
    fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property completes
    fun completes(): BOOL {
        val sel = ObjCRuntime.sel("completes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCompletes(value: BOOL) {
        val sel = ObjCRuntime.sel("setCompletes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSComboBoxDelegate> */
    override fun `delegate`(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    override fun `setDelegate`(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dataSource
    /** @return id<NSComboBoxDataSource> */
    fun dataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("dataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDataSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property objectValueOfSelectedItem
    fun objectValueOfSelectedItem(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValueOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property objectValues
    fun objectValues(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValues")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

