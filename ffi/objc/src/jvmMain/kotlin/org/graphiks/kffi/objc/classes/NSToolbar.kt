package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSToolbar
 * Superclass: NSObject
 */
open class NSToolbar(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSToolbar") }
        
    }
    
    open fun initWithIdentifier(identifier: NSToolbarIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun insertItemWithItemIdentifier_atIndex(itemIdentifier: NSToolbarItemIdentifier, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertItemWithItemIdentifier:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, itemIdentifier, index)
    }
    
    open fun removeItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    open fun removeItemWithItemIdentifier(itemIdentifier: NSToolbarItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("removeItemWithItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, itemIdentifier)
    }
    
    open fun runCustomizationPalette(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("runCustomizationPalette:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun validateVisibleItems(): Unit {
        val sel = ObjCRuntime.sel("validateVisibleItems")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSToolbarDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visible
    open fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationPaletteIsRunning
    open fun customizationPaletteIsRunning(): BOOL {
        val sel = ObjCRuntime.sel("customizationPaletteIsRunning")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property displayMode
    open fun displayMode(): NSToolbarDisplayMode {
        val sel = ObjCRuntime.sel("displayMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarDisplayMode
    }
    open fun setDisplayMode(value: NSToolbarDisplayMode) {
        val sel = ObjCRuntime.sel("setDisplayMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedItemIdentifier
    open fun selectedItemIdentifier(): NSToolbarItemIdentifier {
        val sel = ObjCRuntime.sel("selectedItemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemIdentifier
    }
    open fun setSelectedItemIdentifier(value: NSToolbarItemIdentifier) {
        val sel = ObjCRuntime.sel("setSelectedItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsUserCustomization
    open fun allowsUserCustomization(): BOOL {
        val sel = ObjCRuntime.sel("allowsUserCustomization")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsUserCustomization(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsUserCustomization:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsDisplayModeCustomization
    open fun allowsDisplayModeCustomization(): BOOL {
        val sel = ObjCRuntime.sel("allowsDisplayModeCustomization")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsDisplayModeCustomization(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsDisplayModeCustomization:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property identifier
    open fun identifier(): NSToolbarIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarIdentifier
    }
    
    // @property items
    /** @return NSArray<__kindof NSToolbarItem *> * */
    open fun items(): MemorySegment {
        val sel = ObjCRuntime.sel("items")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property visibleItems
    /** @return NSArray<__kindof NSToolbarItem *> * */
    open fun visibleItems(): MemorySegment {
        val sel = ObjCRuntime.sel("visibleItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property itemIdentifiers
    /** @return NSArray<NSToolbarItemIdentifier> * */
    open fun itemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property centeredItemIdentifiers
    /** @return NSSet<NSToolbarItemIdentifier> * */
    open fun centeredItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("centeredItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCenteredItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCenteredItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosavesConfiguration
    open fun autosavesConfiguration(): BOOL {
        val sel = ObjCRuntime.sel("autosavesConfiguration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAutosavesConfiguration(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutosavesConfiguration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsExtensionItems
    open fun allowsExtensionItems(): BOOL {
        val sel = ObjCRuntime.sel("allowsExtensionItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsExtensionItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsExtensionItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSToolbar ─────────────────────────────────────────

fun NSToolbar.setConfigurationFromDictionary(configDict: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setConfigurationFromDictionary:")
    ObjCRuntime.msgSend(null, ptr, sel, configDict)
}

fun NSToolbar.sizeMode(): NSToolbarSizeMode {
    val sel = ObjCRuntime.sel("sizeMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarSizeMode
}

fun NSToolbar.setSizeMode(sizeMode: NSToolbarSizeMode): Unit {
    val sel = ObjCRuntime.sel("setSizeMode:")
    ObjCRuntime.msgSend(null, ptr, sel, sizeMode)
}

fun NSToolbar.centeredItemIdentifier(): NSToolbarItemIdentifier {
    val sel = ObjCRuntime.sel("centeredItemIdentifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemIdentifier
}

fun NSToolbar.setCenteredItemIdentifier(centeredItemIdentifier: NSToolbarItemIdentifier): Unit {
    val sel = ObjCRuntime.sel("setCenteredItemIdentifier:")
    ObjCRuntime.msgSend(null, ptr, sel, centeredItemIdentifier)
}

fun NSToolbar.fullScreenAccessoryView(): MemorySegment {
    val sel = ObjCRuntime.sel("fullScreenAccessoryView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSToolbar.setFullScreenAccessoryView(fullScreenAccessoryView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryView:")
    ObjCRuntime.msgSend(null, ptr, sel, fullScreenAccessoryView)
}

fun NSToolbar.fullScreenAccessoryViewMinHeight(): CGFloat {
    val sel = ObjCRuntime.sel("fullScreenAccessoryViewMinHeight")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSToolbar.setFullScreenAccessoryViewMinHeight(fullScreenAccessoryViewMinHeight: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryViewMinHeight:")
    ObjCRuntime.msgSend(null, ptr, sel, fullScreenAccessoryViewMinHeight)
}

fun NSToolbar.fullScreenAccessoryViewMaxHeight(): CGFloat {
    val sel = ObjCRuntime.sel("fullScreenAccessoryViewMaxHeight")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSToolbar.setFullScreenAccessoryViewMaxHeight(fullScreenAccessoryViewMaxHeight: CGFloat): Unit {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryViewMaxHeight:")
    ObjCRuntime.msgSend(null, ptr, sel, fullScreenAccessoryViewMaxHeight)
}

fun NSToolbar.showsBaselineSeparator(): BOOL {
    val sel = ObjCRuntime.sel("showsBaselineSeparator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSToolbar.setShowsBaselineSeparator(showsBaselineSeparator: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShowsBaselineSeparator:")
    ObjCRuntime.msgSend(null, ptr, sel, showsBaselineSeparator)
}

/** @return NSDictionary<NSString *,id> * */
fun NSToolbar.configurationDictionary(): MemorySegment {
    val sel = ObjCRuntime.sel("configurationDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property sizeMode
fun NSToolbar.sizeMode(): NSToolbarSizeMode {
    val sel = ObjCRuntime.sel("sizeMode")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarSizeMode
}
fun NSToolbar.setSizeMode(value: NSToolbarSizeMode) {
    val sel = ObjCRuntime.sel("setSizeMode:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property centeredItemIdentifier
fun NSToolbar.centeredItemIdentifier(): NSToolbarItemIdentifier {
    val sel = ObjCRuntime.sel("centeredItemIdentifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSToolbarItemIdentifier
}
fun NSToolbar.setCenteredItemIdentifier(value: NSToolbarItemIdentifier) {
    val sel = ObjCRuntime.sel("setCenteredItemIdentifier:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property fullScreenAccessoryView
fun NSToolbar.fullScreenAccessoryView(): MemorySegment {
    val sel = ObjCRuntime.sel("fullScreenAccessoryView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSToolbar.setFullScreenAccessoryView(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryView:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property fullScreenAccessoryViewMinHeight
fun NSToolbar.fullScreenAccessoryViewMinHeight(): CGFloat {
    val sel = ObjCRuntime.sel("fullScreenAccessoryViewMinHeight")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}
fun NSToolbar.setFullScreenAccessoryViewMinHeight(value: CGFloat) {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryViewMinHeight:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property fullScreenAccessoryViewMaxHeight
fun NSToolbar.fullScreenAccessoryViewMaxHeight(): CGFloat {
    val sel = ObjCRuntime.sel("fullScreenAccessoryViewMaxHeight")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}
fun NSToolbar.setFullScreenAccessoryViewMaxHeight(value: CGFloat) {
    val sel = ObjCRuntime.sel("setFullScreenAccessoryViewMaxHeight:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property showsBaselineSeparator
fun NSToolbar.showsBaselineSeparator(): BOOL {
    val sel = ObjCRuntime.sel("showsBaselineSeparator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSToolbar.setShowsBaselineSeparator(value: BOOL) {
    val sel = ObjCRuntime.sel("setShowsBaselineSeparator:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property configurationDictionary
/** @return NSDictionary<NSString *,id> * */
fun NSToolbar.configurationDictionary(): MemorySegment {
    val sel = ObjCRuntime.sel("configurationDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

