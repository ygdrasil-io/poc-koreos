package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPopUpButtonCell
 * Superclass: NSMenuItemCell
 * Protocols: NSMenuItemValidation
 */
open class NSPopUpButtonCell(ptr: MemorySegment) : NSMenuItemCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPopUpButtonCell") }
        
    }
    
    fun initTextCell_pullsDown(stringValue: MemorySegment, pullDown: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:pullsDown:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, stringValue, pullDown) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initTextCell_pullsDown(stringValue: String, pullDown: BOOL): MemorySegment = initTextCell_pullsDown(ObjCRuntime.newNSString(Arena.global(), stringValue), pullDown)
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun addItemWithTitle(title: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addItemWithTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, title)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addItemWithTitle(title: String): Unit = addItemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun addItemsWithTitles(itemTitles: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addItemsWithTitles:")
        ObjCRuntime.msgSend(null, ptr, sel, itemTitles)
    }
    
    fun insertItemWithTitle_atIndex(title: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertItemWithTitle:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, title, index)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun insertItemWithTitle_atIndex(title: String, index: NSInteger): Unit = insertItemWithTitle_atIndex(ObjCRuntime.newNSString(Arena.global(), title), index)
    
    fun removeItemWithTitle(title: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeItemWithTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, title)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removeItemWithTitle(title: String): Unit = removeItemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun removeItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun removeAllItems(): Unit {
        val sel = ObjCRuntime.sel("removeAllItems")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun indexOfItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    fun indexOfItemWithTitle(title: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, title) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun indexOfItemWithTitle(title: String): NSInteger = indexOfItemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun indexOfItemWithTag(tag: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, tag) as NSInteger
    }
    
    fun indexOfItemWithRepresentedObject(obj: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithRepresentedObject:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, obj) as NSInteger
    }
    
    fun indexOfItemWithTarget_andAction(target: MemorySegment, actionSelector: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTarget:andAction:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, target, actionSelector) as NSInteger
    }
    
    fun itemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun itemWithTitle(title: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("itemWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun itemWithTitle(title: String): MemorySegment = itemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun selectItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    fun selectItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun selectItemWithTitle(title: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectItemWithTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, title)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun selectItemWithTitle(title: String): Unit = selectItemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun selectItemWithTag(tag: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("selectItemWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tag) as BOOL
    }
    
    fun setTitle(string: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, string)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setTitle(string: String): Unit = setTitle(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun synchronizeTitleAndSelectedItem(): Unit {
        val sel = ObjCRuntime.sel("synchronizeTitleAndSelectedItem")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun itemTitleAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemTitleAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun itemTitleAtIndexAsString(index: NSInteger): String = ObjCRuntime.toJavaString(itemTitleAtIndex(index))
    
    fun attachPopUpWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("attachPopUpWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun dismissPopUp(): Unit {
        val sel = ObjCRuntime.sel("dismissPopUp")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun performClickWithFrame_inView(frame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performClickWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pullsDown
    fun pullsDown(): BOOL {
        val sel = ObjCRuntime.sel("pullsDown")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPullsDown(value: BOOL) {
        val sel = ObjCRuntime.sel("setPullsDown:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoenablesItems
    fun autoenablesItems(): BOOL {
        val sel = ObjCRuntime.sel("autoenablesItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutoenablesItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutoenablesItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredEdge
    fun preferredEdge(): NSRectEdge {
        val sel = ObjCRuntime.sel("preferredEdge")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRectEdge
    }
    fun setPreferredEdge(value: NSRectEdge) {
        val sel = ObjCRuntime.sel("setPreferredEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesItemFromMenu
    fun usesItemFromMenu(): BOOL {
        val sel = ObjCRuntime.sel("usesItemFromMenu")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesItemFromMenu(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesItemFromMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property altersStateOfSelectedItem
    fun altersStateOfSelectedItem(): BOOL {
        val sel = ObjCRuntime.sel("altersStateOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAltersStateOfSelectedItem(value: BOOL) {
        val sel = ObjCRuntime.sel("setAltersStateOfSelectedItem:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemArray
    /** @return NSArray<NSMenuItem *> * */
    fun itemArray(): MemorySegment {
        val sel = ObjCRuntime.sel("itemArray")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfItems
    fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property lastItem
    fun lastItem(): MemorySegment {
        val sel = ObjCRuntime.sel("lastItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedItem
    fun selectedItem(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property indexOfSelectedItem
    fun indexOfSelectedItem(): NSInteger {
        val sel = ObjCRuntime.sel("indexOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property itemTitles
    /** @return NSArray<NSString *> * */
    fun itemTitles(): MemorySegment {
        val sel = ObjCRuntime.sel("itemTitles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property titleOfSelectedItem
    fun titleOfSelectedItem(): MemorySegment {
        val sel = ObjCRuntime.sel("titleOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleOfSelectedItemAsString(): String = ObjCRuntime.toJavaString(titleOfSelectedItem())
    
    // @property arrowPosition
    fun arrowPosition(): NSPopUpArrowPosition {
        val sel = ObjCRuntime.sel("arrowPosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPopUpArrowPosition
    }
    fun setArrowPosition(value: NSPopUpArrowPosition) {
        val sel = ObjCRuntime.sel("setArrowPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

