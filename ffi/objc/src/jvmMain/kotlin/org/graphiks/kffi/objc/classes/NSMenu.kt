package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMenu
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding, NSUserInterfaceItemIdentification, NSAppearanceCustomization, NSAccessibilityElement, NSAccessibility
 */
open class NSMenu(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMenu") }
        
        open fun popUpContextMenu_withEvent_forView(menu: MemorySegment, event: MemorySegment, view: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("popUpContextMenu:withEvent:forView:")
            ObjCRuntime.msgSend(null, _class, sel, menu, event, view)
        }
        
        open fun popUpContextMenu_withEvent_forView_withFont(menu: MemorySegment, event: MemorySegment, view: MemorySegment, font: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("popUpContextMenu:withEvent:forView:withFont:")
            ObjCRuntime.msgSend(null, _class, sel, menu, event, view, font)
        }
        
        open fun setMenuBarVisible(visible: BOOL): Unit {
            val sel = ObjCRuntime.sel("setMenuBarVisible:")
            ObjCRuntime.msgSend(null, _class, sel, visible)
        }
        
        open fun menuBarVisible(): BOOL {
            val sel = ObjCRuntime.sel("menuBarVisible")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun initWithTitle(title: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithTitle(title: String): MemorySegment = initWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun popUpMenuPositioningItem_atLocation_inView(item: MemorySegment, location: NSPoint, view: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("popUpMenuPositioningItem:atLocation:inView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), view) as BOOL
    }
    
    open fun insertItem_atIndex(newItem: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertItem:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, newItem, index)
    }
    
    open fun addItem(newItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addItem:")
        ObjCRuntime.msgSend(null, ptr, sel, newItem)
    }
    
    open fun insertItemWithTitle_action_keyEquivalent_atIndex(string: MemorySegment, selector: MemorySegment, charCode: MemorySegment, index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("insertItemWithTitle:action:keyEquivalent:atIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, selector, charCode, index) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun insertItemWithTitle_action_keyEquivalent_atIndex(string: String, selector: MemorySegment, charCode: String, index: NSInteger): MemorySegment = insertItemWithTitle_action_keyEquivalent_atIndex(ObjCRuntime.newNSString(Arena.global(), string), selector, ObjCRuntime.newNSString(Arena.global(), charCode), index)
    
    open fun addItemWithTitle_action_keyEquivalent(string: MemorySegment, selector: MemorySegment, charCode: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addItemWithTitle:action:keyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, selector, charCode) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addItemWithTitle_action_keyEquivalent(string: String, selector: MemorySegment, charCode: String): MemorySegment = addItemWithTitle_action_keyEquivalent(ObjCRuntime.newNSString(Arena.global(), string), selector, ObjCRuntime.newNSString(Arena.global(), charCode))
    
    open fun removeItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    open fun removeItem(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeItem:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    open fun setSubmenu_forItem(menu: MemorySegment, item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setSubmenu:forItem:")
        ObjCRuntime.msgSend(null, ptr, sel, menu, item)
    }
    
    open fun removeAllItems(): Unit {
        val sel = ObjCRuntime.sel("removeAllItems")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun itemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    open fun indexOfItem(item: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, item) as NSInteger
    }
    
    open fun indexOfItemWithTitle(title: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, title) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun indexOfItemWithTitle(title: String): NSInteger = indexOfItemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    open fun indexOfItemWithTag(tag: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, tag) as NSInteger
    }
    
    open fun indexOfItemWithRepresentedObject(`object`: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithRepresentedObject:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, `object`) as NSInteger
    }
    
    open fun indexOfItemWithSubmenu(submenu: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithSubmenu:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, submenu) as NSInteger
    }
    
    open fun indexOfItemWithTarget_andAction(target: MemorySegment, actionSelector: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemWithTarget:andAction:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, target, actionSelector) as NSInteger
    }
    
    open fun itemWithTitle(title: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("itemWithTitle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun itemWithTitle(title: String): MemorySegment = itemWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
    
    open fun itemWithTag(tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tag) as MemorySegment
    }
    
    open fun update(): Unit {
        val sel = ObjCRuntime.sel("update")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun performKeyEquivalent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performKeyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    open fun itemChanged(item: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("itemChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, item)
    }
    
    open fun performActionForItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("performActionForItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    open fun cancelTracking(): Unit {
        val sel = ObjCRuntime.sel("cancelTracking")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun cancelTrackingWithoutAnimation(): Unit {
        val sel = ObjCRuntime.sel("cancelTrackingWithoutAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property title
    open fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property supermenu
    open fun supermenu(): MemorySegment {
        val sel = ObjCRuntime.sel("supermenu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSupermenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSupermenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemArray
    /** @return NSArray<NSMenuItem *> * */
    open fun itemArray(): MemorySegment {
        val sel = ObjCRuntime.sel("itemArray")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setItemArray(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setItemArray:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfItems
    open fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property autoenablesItems
    open fun autoenablesItems(): BOOL {
        val sel = ObjCRuntime.sel("autoenablesItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAutoenablesItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutoenablesItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSMenuDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property menuBarHeight
    open fun menuBarHeight(): CGFloat {
        val sel = ObjCRuntime.sel("menuBarHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property highlightedItem
    open fun highlightedItem(): MemorySegment {
        val sel = ObjCRuntime.sel("highlightedItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property minimumWidth
    open fun minimumWidth(): CGFloat {
        val sel = ObjCRuntime.sel("minimumWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMinimumWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property size
    open fun size(): NSSize {
        val sel = ObjCRuntime.sel("size")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property font
    open fun font(): MemorySegment {
        val sel = ObjCRuntime.sel("font")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsContextMenuPlugIns
    open fun allowsContextMenuPlugIns(): BOOL {
        val sel = ObjCRuntime.sel("allowsContextMenuPlugIns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsContextMenuPlugIns(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsContextMenuPlugIns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticallyInsertsWritingToolsItems
    open fun automaticallyInsertsWritingToolsItems(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyInsertsWritingToolsItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAutomaticallyInsertsWritingToolsItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyInsertsWritingToolsItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsStateColumn
    open fun showsStateColumn(): BOOL {
        val sel = ObjCRuntime.sel("showsStateColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setShowsStateColumn(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsStateColumn:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userInterfaceLayoutDirection
    open fun userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    open fun setUserInterfaceLayoutDirection(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSPaletteMenus on NSMenu ─────────────────────────────────────────

fun NSMenu.presentationStyle(): NSMenuPresentationStyle {
    val sel = ObjCRuntime.sel("presentationStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuPresentationStyle
}

fun NSMenu.setPresentationStyle(presentationStyle: NSMenuPresentationStyle): Unit {
    val sel = ObjCRuntime.sel("setPresentationStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, presentationStyle)
}

fun NSMenu.selectionMode(): NSMenuSelectionMode {
    val sel = ObjCRuntime.sel("selectionMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuSelectionMode
}

fun NSMenu.setSelectionMode(selectionMode: NSMenuSelectionMode): Unit {
    val sel = ObjCRuntime.sel("setSelectionMode:")
    ObjCRuntime.msgSend(null, ptr, sel, selectionMode)
}

/** @return NSArray<NSMenuItem *> * */
fun NSMenu.selectedItems(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedItems")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenu.setSelectedItems(selectedItems: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setSelectedItems:")
    ObjCRuntime.msgSend(null, ptr, sel, selectedItems)
}

// Class<*> method: +[NSMenu paletteMenuWithColors:titles:selectionHandler:]
fun NSMenu_paletteMenuWithColors_titles_selectionHandler(colors: MemorySegment, itemTitles: MemorySegment, onSelectionChange: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("paletteMenuWithColors:titles:selectionHandler:")
    val cls = ObjCRuntime.getClass("NSMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, colors, itemTitles, onSelectionChange) as MemorySegment
}

// Class<*> method: +[NSMenu paletteMenuWithColors:titles:templateImage:selectionHandler:]
fun NSMenu_paletteMenuWithColors_titles_templateImage_selectionHandler(colors: MemorySegment, itemTitles: MemorySegment, image: MemorySegment, onSelectionChange: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("paletteMenuWithColors:titles:templateImage:selectionHandler:")
    val cls = ObjCRuntime.getClass("NSMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, colors, itemTitles, image, onSelectionChange) as MemorySegment
}

// @property presentationStyle
fun NSMenu.presentationStyle(): NSMenuPresentationStyle {
    val sel = ObjCRuntime.sel("presentationStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuPresentationStyle
}
fun NSMenu.setPresentationStyle(value: NSMenuPresentationStyle) {
    val sel = ObjCRuntime.sel("setPresentationStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property selectionMode
fun NSMenu.selectionMode(): NSMenuSelectionMode {
    val sel = ObjCRuntime.sel("selectionMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuSelectionMode
}
fun NSMenu.setSelectionMode(value: NSMenuSelectionMode) {
    val sel = ObjCRuntime.sel("setSelectionMode:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property selectedItems
/** @return NSArray<NSMenuItem *> * */
fun NSMenu.selectedItems(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedItems")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSMenu.setSelectedItems(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setSelectedItems:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSSubmenuAction on NSMenu ─────────────────────────────────────────

fun NSMenu.submenuAction(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("submenuAction:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSMenuPropertiesToUpdate on NSMenu ─────────────────────────────────────────

fun NSMenu.propertiesToUpdate(): NSMenuProperties {
    val sel = ObjCRuntime.sel("propertiesToUpdate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuProperties
}

// @property propertiesToUpdate
fun NSMenu.propertiesToUpdate(): NSMenuProperties {
    val sel = ObjCRuntime.sel("propertiesToUpdate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMenuProperties
}

// ── Category: NSDeprecated on NSMenu ─────────────────────────────────────────

fun NSMenu.setMenuRepresentation(menuRep: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setMenuRepresentation:")
    ObjCRuntime.msgSend(null, ptr, sel, menuRep)
}

fun NSMenu.menuRepresentation(): MemorySegment {
    val sel = ObjCRuntime.sel("menuRepresentation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenu.setContextMenuRepresentation(menuRep: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setContextMenuRepresentation:")
    ObjCRuntime.msgSend(null, ptr, sel, menuRep)
}

fun NSMenu.contextMenuRepresentation(): MemorySegment {
    val sel = ObjCRuntime.sel("contextMenuRepresentation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenu.setTearOffMenuRepresentation(menuRep: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTearOffMenuRepresentation:")
    ObjCRuntime.msgSend(null, ptr, sel, menuRep)
}

fun NSMenu.tearOffMenuRepresentation(): MemorySegment {
    val sel = ObjCRuntime.sel("tearOffMenuRepresentation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenu.attachedMenu(): MemorySegment {
    val sel = ObjCRuntime.sel("attachedMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenu.isAttached(): BOOL {
    val sel = ObjCRuntime.sel("isAttached")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSMenu.sizeToFit(): Unit {
    val sel = ObjCRuntime.sel("sizeToFit")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSMenu.locationForSubmenu(submenu: MemorySegment): NSPoint {
    val sel = ObjCRuntime.sel("locationForSubmenu:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, submenu) as NSPoint
}

fun NSMenu.helpRequested(eventPtr: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("helpRequested:")
    ObjCRuntime.msgSend(null, ptr, sel, eventPtr)
}

fun NSMenu.menuChangedMessagesEnabled(): BOOL {
    val sel = ObjCRuntime.sel("menuChangedMessagesEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSMenu.setMenuChangedMessagesEnabled(menuChangedMessagesEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setMenuChangedMessagesEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, menuChangedMessagesEnabled)
}

fun NSMenu.isTornOff(): BOOL {
    val sel = ObjCRuntime.sel("isTornOff")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class<*> method: +[NSMenu menuZone]
fun NSMenu_menuZone(): MemorySegment {
    val sel = ObjCRuntime.sel("menuZone")
    val cls = ObjCRuntime.getClass("NSMenu")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSMenu setMenuZone:]
fun NSMenu_setMenuZone(zone: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setMenuZone:")
    val cls = ObjCRuntime.getClass("NSMenu")
    ObjCRuntime.msgSend(null, cls, sel, zone)
}

// @property menuChangedMessagesEnabled
fun NSMenu.menuChangedMessagesEnabled(): BOOL {
    val sel = ObjCRuntime.sel("menuChangedMessagesEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSMenu.setMenuChangedMessagesEnabled(value: BOOL) {
    val sel = ObjCRuntime.sel("setMenuChangedMessagesEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property tornOff
fun NSMenu.isTornOff(): BOOL {
    val sel = ObjCRuntime.sel("isTornOff")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

