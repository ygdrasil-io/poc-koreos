package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTouchBar
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSTouchBar(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTouchBar") }
        
        open fun isAutomaticCustomizeTouchBarMenuItemEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticCustomizeTouchBarMenuItemEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun setAutomaticCustomizeTouchBarMenuItemEnabled(automaticCustomizeTouchBarMenuItemEnabled: BOOL): Unit {
            val sel = ObjCRuntime.sel("setAutomaticCustomizeTouchBarMenuItemEnabled:")
            ObjCRuntime.msgSend(null, _class, sel, automaticCustomizeTouchBarMenuItemEnabled)
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun itemForIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("itemForIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    // @property customizationIdentifier
    open fun customizationIdentifier(): NSTouchBarCustomizationIdentifier {
        val sel = ObjCRuntime.sel("customizationIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarCustomizationIdentifier
    }
    open fun setCustomizationIdentifier(value: NSTouchBarCustomizationIdentifier) {
        val sel = ObjCRuntime.sel("setCustomizationIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationAllowedItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    open fun customizationAllowedItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationAllowedItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCustomizationAllowedItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationAllowedItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationRequiredItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    open fun customizationRequiredItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationRequiredItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCustomizationRequiredItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationRequiredItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    open fun defaultItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDefaultItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDefaultItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    open fun itemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property principalItemIdentifier
    open fun principalItemIdentifier(): NSTouchBarItemIdentifier {
        val sel = ObjCRuntime.sel("principalItemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarItemIdentifier
    }
    open fun setPrincipalItemIdentifier(value: NSTouchBarItemIdentifier) {
        val sel = ObjCRuntime.sel("setPrincipalItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property escapeKeyReplacementItemIdentifier
    open fun escapeKeyReplacementItemIdentifier(): NSTouchBarItemIdentifier {
        val sel = ObjCRuntime.sel("escapeKeyReplacementItemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarItemIdentifier
    }
    open fun setEscapeKeyReplacementItemIdentifier(value: NSTouchBarItemIdentifier) {
        val sel = ObjCRuntime.sel("setEscapeKeyReplacementItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property templateItems
    /** @return NSSet<NSTouchBarItem *> * */
    open fun templateItems(): MemorySegment {
        val sel = ObjCRuntime.sel("templateItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTemplateItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTemplateItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSTouchBarDelegate> */
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
    
    // @property automaticCustomizeTouchBarMenuItemEnabled
}

