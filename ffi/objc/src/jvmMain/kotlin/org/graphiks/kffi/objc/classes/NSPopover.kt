/**
 * Kotlin/JVM wrapper for Objective-C class: NSPopover
 * Superclass: NSResponder
 * Protocols: NSAppearanceCustomization, NSAccessibilityElement, NSAccessibility
 */
open class NSPopover(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPopover") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun showRelativeToRect_ofView_preferredEdge(positioningRect: NSRect, positioningView: MemorySegment, preferredEdge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("showRelativeToRect:ofView:preferredEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(positioningRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), positioningView, preferredEdge)
    }
    
    fun showRelativeToToolbarItem(toolbarItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showRelativeToToolbarItem:")
        ObjCRuntime.msgSend(null, ptr, sel, toolbarItem)
    }
    
    fun performClose(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performClose:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSPopoverDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property appearance
    fun appearance(): MemorySegment {
        val sel = ObjCRuntime.sel("appearance")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAppearance(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAppearance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property effectiveAppearance
    fun effectiveAppearance(): MemorySegment {
        val sel = ObjCRuntime.sel("effectiveAppearance")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property behavior
    fun behavior(): NSPopoverBehavior {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPopoverBehavior
    }
    fun setBehavior(value: NSPopoverBehavior) {
        val sel = ObjCRuntime.sel("setBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animates
    fun animates(): BOOL {
        val sel = ObjCRuntime.sel("animates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAnimates(value: BOOL) {
        val sel = ObjCRuntime.sel("setAnimates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentViewController
    fun contentViewController(): MemorySegment {
        val sel = ObjCRuntime.sel("contentViewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property shown
    fun isShown(): BOOL {
        val sel = ObjCRuntime.sel("isShown")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property detached
    fun isDetached(): BOOL {
        val sel = ObjCRuntime.sel("isDetached")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property positioningRect
    fun positioningRect(): NSRect {
        val sel = ObjCRuntime.sel("positioningRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setPositioningRect(value: NSRect) {
        val sel = ObjCRuntime.sel("setPositioningRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property hasFullSizeContent
    fun hasFullSizeContent(): BOOL {
        val sel = ObjCRuntime.sel("hasFullSizeContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasFullSizeContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasFullSizeContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

