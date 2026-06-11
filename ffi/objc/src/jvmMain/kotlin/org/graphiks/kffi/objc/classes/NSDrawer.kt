/**
 * Kotlin/JVM wrapper for Objective-C class: NSDrawer
 * Superclass: NSResponder
 * Protocols: NSAccessibilityElement, NSAccessibility
 */
open class NSDrawer(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDrawer") }
        
    }
    
    fun initWithContentSize_preferredEdge(contentSize: NSSize, edge: NSRectEdge): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentSize:preferredEdge:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(contentSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), edge) as MemorySegment
    }
    
    fun open(): Unit {
        val sel = ObjCRuntime.sel("open")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun openOnEdge(edge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("openOnEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, edge)
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun toggle(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggle:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property parentWindow
    fun parentWindow(): MemorySegment {
        val sel = ObjCRuntime.sel("parentWindow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setParentWindow(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setParentWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentView
    fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
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
    
    // @property delegate
    /** @return id<NSDrawerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    fun state(): NSInteger {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property edge
    fun edge(): NSRectEdge {
        val sel = ObjCRuntime.sel("edge")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRectEdge
    }
    
    // @property contentSize
    fun contentSize(): NSSize {
        val sel = ObjCRuntime.sel("contentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property minContentSize
    fun minContentSize(): NSSize {
        val sel = ObjCRuntime.sel("minContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinContentSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property maxContentSize
    fun maxContentSize(): NSSize {
        val sel = ObjCRuntime.sel("maxContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMaxContentSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMaxContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property leadingOffset
    fun leadingOffset(): CGFloat {
        val sel = ObjCRuntime.sel("leadingOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLeadingOffset(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLeadingOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property trailingOffset
    fun trailingOffset(): CGFloat {
        val sel = ObjCRuntime.sel("trailingOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTrailingOffset(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTrailingOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

