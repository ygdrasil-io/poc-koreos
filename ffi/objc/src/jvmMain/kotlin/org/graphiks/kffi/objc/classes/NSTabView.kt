/**
 * Kotlin/JVM wrapper for Objective-C class: NSTabView
 * Superclass: NSView
 */
open class NSTabView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTabView") }
        
    }
    
    fun selectTabViewItem(tabViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, tabViewItem)
    }
    
    fun selectTabViewItemAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectTabViewItemAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun selectTabViewItemWithIdentifier(identifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectTabViewItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, identifier)
    }
    
    fun takeSelectedTabViewItemFromSender(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeSelectedTabViewItemFromSender:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectFirstTabViewItem(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectFirstTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectLastTabViewItem(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectLastTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectNextTabViewItem(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectNextTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectPreviousTabViewItem(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectPreviousTabViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
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
    
    fun tabViewItemAtPoint(point: NSPoint): MemorySegment {
        val sel = ObjCRuntime.sel("tabViewItemAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    fun indexOfTabViewItem(tabViewItem: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfTabViewItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, tabViewItem) as NSInteger
    }
    
    fun tabViewItemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("tabViewItemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun indexOfTabViewItemWithIdentifier(identifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfTabViewItemWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, identifier) as NSInteger
    }
    
    // @property selectedTabViewItem
    fun selectedTabViewItem(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedTabViewItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property font
    fun font(): MemorySegment {
        val sel = ObjCRuntime.sel("font")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabViewType
    fun tabViewType(): NSTabViewType {
        val sel = ObjCRuntime.sel("tabViewType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTabViewType
    }
    fun setTabViewType(value: NSTabViewType) {
        val sel = ObjCRuntime.sel("setTabViewType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabPosition
    fun tabPosition(): NSTabPosition {
        val sel = ObjCRuntime.sel("tabPosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTabPosition
    }
    fun setTabPosition(value: NSTabPosition) {
        val sel = ObjCRuntime.sel("setTabPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabViewBorderType
    fun tabViewBorderType(): NSTabViewBorderType {
        val sel = ObjCRuntime.sel("tabViewBorderType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTabViewBorderType
    }
    fun setTabViewBorderType(value: NSTabViewBorderType) {
        val sel = ObjCRuntime.sel("setTabViewBorderType:")
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
    
    // @property allowsTruncatedLabels
    fun allowsTruncatedLabels(): BOOL {
        val sel = ObjCRuntime.sel("allowsTruncatedLabels")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsTruncatedLabels(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsTruncatedLabels:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumSize
    fun minimumSize(): NSSize {
        val sel = ObjCRuntime.sel("minimumSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property drawsBackground
    fun drawsBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property controlSize
    fun controlSize(): NSControlSize {
        val sel = ObjCRuntime.sel("controlSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlSize
    }
    fun setControlSize(value: NSControlSize) {
        val sel = ObjCRuntime.sel("setControlSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSTabViewDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentRect
    fun contentRect(): NSRect {
        val sel = ObjCRuntime.sel("contentRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property numberOfTabViewItems
    fun numberOfTabViewItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfTabViewItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property controlTint
    fun controlTint(): NSControlTint {
        val sel = ObjCRuntime.sel("controlTint")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
    }
    fun setControlTint(value: NSControlTint) {
        val sel = ObjCRuntime.sel("setControlTint:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

