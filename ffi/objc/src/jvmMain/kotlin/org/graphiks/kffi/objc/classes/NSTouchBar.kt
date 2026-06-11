/**
 * Kotlin/JVM wrapper for Objective-C class: NSTouchBar
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSTouchBar(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTouchBar") }
        
        fun isAutomaticCustomizeTouchBarMenuItemEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticCustomizeTouchBarMenuItemEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun setAutomaticCustomizeTouchBarMenuItemEnabled(automaticCustomizeTouchBarMenuItemEnabled: BOOL): Unit {
            val sel = ObjCRuntime.sel("setAutomaticCustomizeTouchBarMenuItemEnabled:")
            ObjCRuntime.msgSend(null, _class, sel, automaticCustomizeTouchBarMenuItemEnabled)
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun itemForIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("itemForIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    // @property customizationIdentifier
    fun customizationIdentifier(): NSTouchBarCustomizationIdentifier {
        val sel = ObjCRuntime.sel("customizationIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarCustomizationIdentifier
    }
    fun setCustomizationIdentifier(value: NSTouchBarCustomizationIdentifier) {
        val sel = ObjCRuntime.sel("setCustomizationIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationAllowedItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    fun customizationAllowedItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationAllowedItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationAllowedItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationAllowedItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationRequiredItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    fun customizationRequiredItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationRequiredItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationRequiredItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationRequiredItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultItemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    fun defaultItemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultItemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDefaultItemIdentifiers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDefaultItemIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemIdentifiers
    /** @return NSArray<NSTouchBarItemIdentifier> * */
    fun itemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property principalItemIdentifier
    fun principalItemIdentifier(): NSTouchBarItemIdentifier {
        val sel = ObjCRuntime.sel("principalItemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarItemIdentifier
    }
    fun setPrincipalItemIdentifier(value: NSTouchBarItemIdentifier) {
        val sel = ObjCRuntime.sel("setPrincipalItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property escapeKeyReplacementItemIdentifier
    fun escapeKeyReplacementItemIdentifier(): NSTouchBarItemIdentifier {
        val sel = ObjCRuntime.sel("escapeKeyReplacementItemIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchBarItemIdentifier
    }
    fun setEscapeKeyReplacementItemIdentifier(value: NSTouchBarItemIdentifier) {
        val sel = ObjCRuntime.sel("setEscapeKeyReplacementItemIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property templateItems
    /** @return NSSet<NSTouchBarItem *> * */
    fun templateItems(): MemorySegment {
        val sel = ObjCRuntime.sel("templateItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTemplateItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTemplateItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSTouchBarDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticCustomizeTouchBarMenuItemEnabled
    fun isAutomaticCustomizeTouchBarMenuItemEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticCustomizeTouchBarMenuItemEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticCustomizeTouchBarMenuItemEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticCustomizeTouchBarMenuItemEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

