/**
 * Kotlin/JVM wrapper for Objective-C class: NSClipView
 * Superclass: NSView
 */
open class NSClipView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSClipView") }
        
    }
    
    fun viewFrameChanged(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewFrameChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun viewBoundsChanged(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewBoundsChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun autoscroll(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("autoscroll:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun scrollToPoint(newOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("scrollToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun constrainBoundsRect(proposedBounds: NSRect): NSRect {
        val sel = ObjCRuntime.sel("constrainBoundsRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(proposedBounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property documentView
    fun documentView(): MemorySegment {
        val sel = ObjCRuntime.sel("documentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDocumentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDocumentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property documentRect
    fun documentRect(): NSRect {
        val sel = ObjCRuntime.sel("documentRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property documentCursor
    fun documentCursor(): MemorySegment {
        val sel = ObjCRuntime.sel("documentCursor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDocumentCursor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDocumentCursor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property documentVisibleRect
    fun documentVisibleRect(): NSRect {
        val sel = ObjCRuntime.sel("documentVisibleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property contentInsets
    fun contentInsets(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("contentInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setContentInsets(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
    // @property automaticallyAdjustsContentInsets
    fun automaticallyAdjustsContentInsets(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyAdjustsContentInsets")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyAdjustsContentInsets(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyAdjustsContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category:  on NSClipView ─────────────────────────────────────────

fun NSClipView.constrainScrollPoint(newOrigin: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("constrainScrollPoint:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, newOrigin) as NSPoint
}

fun NSClipView.copiesOnScroll(): BOOL {
    val sel = ObjCRuntime.sel("copiesOnScroll")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSClipView.setCopiesOnScroll(copiesOnScroll: BOOL): Unit {
    val sel = ObjCRuntime.sel("setCopiesOnScroll:")
    ObjCRuntime.msgSend(null, ptr, sel, copiesOnScroll)
}

// @property copiesOnScroll
fun NSClipView.copiesOnScroll(): BOOL {
    val sel = ObjCRuntime.sel("copiesOnScroll")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSClipView.setCopiesOnScroll(value: BOOL) {
    val sel = ObjCRuntime.sel("setCopiesOnScroll:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

