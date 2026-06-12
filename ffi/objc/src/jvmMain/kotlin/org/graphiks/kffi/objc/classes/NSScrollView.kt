package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrollView
 * Superclass: NSView
 * Protocols: NSTextFinderBarContainer
 */
open class NSScrollView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrollView") }
        
        fun frameSizeForContentSize_horizontalScrollerClass_verticalScrollerClass_borderType_controlSize_scrollerStyle(cSize: NSSize, horizontalScrollerClass: Class<*>, verticalScrollerClass: Class<*>, type: NSBorderType, controlSize: NSControlSize, scrollerStyle: NSScrollerStyle): NSSize {
            val sel = ObjCRuntime.sel("frameSizeForContentSize:horizontalScrollerClass:verticalScrollerClass:borderType:controlSize:scrollerStyle:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), _class, sel, ObjCRuntime.ObjCStructArg(cSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), horizontalScrollerClass, verticalScrollerClass, type, controlSize, scrollerStyle) as NSSize
        }
        
        fun contentSizeForFrameSize_horizontalScrollerClass_verticalScrollerClass_borderType_controlSize_scrollerStyle(fSize: NSSize, horizontalScrollerClass: Class<*>, verticalScrollerClass: Class<*>, type: NSBorderType, controlSize: NSControlSize, scrollerStyle: NSScrollerStyle): NSSize {
            val sel = ObjCRuntime.sel("contentSizeForFrameSize:horizontalScrollerClass:verticalScrollerClass:borderType:controlSize:scrollerStyle:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), _class, sel, ObjCRuntime.ObjCStructArg(fSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), horizontalScrollerClass, verticalScrollerClass, type, controlSize, scrollerStyle) as NSSize
        }
        
        fun frameSizeForContentSize_hasHorizontalScroller_hasVerticalScroller_borderType(cSize: NSSize, hFlag: BOOL, vFlag: BOOL, type: NSBorderType): NSSize {
            val sel = ObjCRuntime.sel("frameSizeForContentSize:hasHorizontalScroller:hasVerticalScroller:borderType:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), _class, sel, ObjCRuntime.ObjCStructArg(cSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), hFlag, vFlag, type) as NSSize
        }
        
        fun contentSizeForFrameSize_hasHorizontalScroller_hasVerticalScroller_borderType(fSize: NSSize, hFlag: BOOL, vFlag: BOOL, type: NSBorderType): NSSize {
            val sel = ObjCRuntime.sel("contentSizeForFrameSize:hasHorizontalScroller:hasVerticalScroller:borderType:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), _class, sel, ObjCRuntime.ObjCStructArg(fSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), hFlag, vFlag, type) as NSSize
        }
        
    }
    
    override fun `initWithFrame`(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun tile(): Unit {
        val sel = ObjCRuntime.sel("tile")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    override fun `reflectScrolledClipView`(cView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reflectScrolledClipView:")
        ObjCRuntime.msgSend(null, ptr, sel, cView)
    }
    
    fun scrollWheel(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("scrollWheel:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun flashScrollers(): Unit {
        val sel = ObjCRuntime.sel("flashScrollers")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun magnifyToFitRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("magnifyToFitRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun setMagnification_centeredAtPoint(magnification: CGFloat, point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("setMagnification:centeredAtPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, magnification, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun addFloatingSubview_forAxis(view: MemorySegment, axis: NSEventGestureAxis): Unit {
        val sel = ObjCRuntime.sel("addFloatingSubview:forAxis:")
        ObjCRuntime.msgSend(null, ptr, sel, view, axis)
    }
    
    // @property documentVisibleRect
    fun documentVisibleRect(): NSRect {
        val sel = ObjCRuntime.sel("documentVisibleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property contentSize
    fun contentSize(): NSSize {
        val sel = ObjCRuntime.sel("contentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
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
    
    // @property contentView
    fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property borderType
    fun borderType(): NSBorderType {
        val sel = ObjCRuntime.sel("borderType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBorderType
    }
    fun setBorderType(value: NSBorderType) {
        val sel = ObjCRuntime.sel("setBorderType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property hasVerticalScroller
    fun hasVerticalScroller(): BOOL {
        val sel = ObjCRuntime.sel("hasVerticalScroller")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasVerticalScroller(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasVerticalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasHorizontalScroller
    fun hasHorizontalScroller(): BOOL {
        val sel = ObjCRuntime.sel("hasHorizontalScroller")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasHorizontalScroller(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasHorizontalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalScroller
    fun verticalScroller(): MemorySegment {
        val sel = ObjCRuntime.sel("verticalScroller")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVerticalScroller(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVerticalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalScroller
    fun horizontalScroller(): MemorySegment {
        val sel = ObjCRuntime.sel("horizontalScroller")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHorizontalScroller(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHorizontalScroller:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autohidesScrollers
    fun autohidesScrollers(): BOOL {
        val sel = ObjCRuntime.sel("autohidesScrollers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutohidesScrollers(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutohidesScrollers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalLineScroll
    fun horizontalLineScroll(): CGFloat {
        val sel = ObjCRuntime.sel("horizontalLineScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setHorizontalLineScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setHorizontalLineScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalLineScroll
    fun verticalLineScroll(): CGFloat {
        val sel = ObjCRuntime.sel("verticalLineScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setVerticalLineScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setVerticalLineScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineScroll
    fun lineScroll(): CGFloat {
        val sel = ObjCRuntime.sel("lineScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLineScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalPageScroll
    fun horizontalPageScroll(): CGFloat {
        val sel = ObjCRuntime.sel("horizontalPageScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setHorizontalPageScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setHorizontalPageScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalPageScroll
    fun verticalPageScroll(): CGFloat {
        val sel = ObjCRuntime.sel("verticalPageScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setVerticalPageScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setVerticalPageScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pageScroll
    fun pageScroll(): CGFloat {
        val sel = ObjCRuntime.sel("pageScroll")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setPageScroll(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPageScroll:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scrollsDynamically
    fun scrollsDynamically(): BOOL {
        val sel = ObjCRuntime.sel("scrollsDynamically")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setScrollsDynamically(value: BOOL) {
        val sel = ObjCRuntime.sel("setScrollsDynamically:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scrollerStyle
    fun scrollerStyle(): NSScrollerStyle {
        val sel = ObjCRuntime.sel("scrollerStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerStyle
    }
    fun setScrollerStyle(value: NSScrollerStyle) {
        val sel = ObjCRuntime.sel("setScrollerStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scrollerKnobStyle
    fun scrollerKnobStyle(): NSScrollerKnobStyle {
        val sel = ObjCRuntime.sel("scrollerKnobStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollerKnobStyle
    }
    fun setScrollerKnobStyle(value: NSScrollerKnobStyle) {
        val sel = ObjCRuntime.sel("setScrollerKnobStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalScrollElasticity
    fun horizontalScrollElasticity(): NSScrollElasticity {
        val sel = ObjCRuntime.sel("horizontalScrollElasticity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollElasticity
    }
    fun setHorizontalScrollElasticity(value: NSScrollElasticity) {
        val sel = ObjCRuntime.sel("setHorizontalScrollElasticity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalScrollElasticity
    fun verticalScrollElasticity(): NSScrollElasticity {
        val sel = ObjCRuntime.sel("verticalScrollElasticity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollElasticity
    }
    fun setVerticalScrollElasticity(value: NSScrollElasticity) {
        val sel = ObjCRuntime.sel("setVerticalScrollElasticity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesPredominantAxisScrolling
    fun usesPredominantAxisScrolling(): BOOL {
        val sel = ObjCRuntime.sel("usesPredominantAxisScrolling")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesPredominantAxisScrolling(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesPredominantAxisScrolling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsMagnification
    fun allowsMagnification(): BOOL {
        val sel = ObjCRuntime.sel("allowsMagnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsMagnification(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsMagnification:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property magnification
    fun magnification(): CGFloat {
        val sel = ObjCRuntime.sel("magnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMagnification(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMagnification:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxMagnification
    fun maxMagnification(): CGFloat {
        val sel = ObjCRuntime.sel("maxMagnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaxMagnification(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaxMagnification:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minMagnification
    fun minMagnification(): CGFloat {
        val sel = ObjCRuntime.sel("minMagnification")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinMagnification(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinMagnification:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property contentInsets
    fun contentInsets(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("contentInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setContentInsets(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
    // @property scrollerInsets
    fun scrollerInsets(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("scrollerInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setScrollerInsets(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setScrollerInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
}

// ── Category: NSRulerSupport on NSScrollView ─────────────────────────────────────────

fun NSScrollView.rulersVisible(): BOOL {
    val sel = ObjCRuntime.sel("rulersVisible")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSScrollView.setRulersVisible(rulersVisible: BOOL): Unit {
    val sel = ObjCRuntime.sel("setRulersVisible:")
    ObjCRuntime.msgSend(null, ptr, sel, rulersVisible)
}

fun NSScrollView.hasHorizontalRuler(): BOOL {
    val sel = ObjCRuntime.sel("hasHorizontalRuler")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSScrollView.setHasHorizontalRuler(hasHorizontalRuler: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHasHorizontalRuler:")
    ObjCRuntime.msgSend(null, ptr, sel, hasHorizontalRuler)
}

fun NSScrollView.hasVerticalRuler(): BOOL {
    val sel = ObjCRuntime.sel("hasVerticalRuler")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSScrollView.setHasVerticalRuler(hasVerticalRuler: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHasVerticalRuler:")
    ObjCRuntime.msgSend(null, ptr, sel, hasVerticalRuler)
}

fun NSScrollView.horizontalRulerView(): MemorySegment {
    val sel = ObjCRuntime.sel("horizontalRulerView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSScrollView.setHorizontalRulerView(horizontalRulerView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setHorizontalRulerView:")
    ObjCRuntime.msgSend(null, ptr, sel, horizontalRulerView)
}

fun NSScrollView.verticalRulerView(): MemorySegment {
    val sel = ObjCRuntime.sel("verticalRulerView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSScrollView.setVerticalRulerView(verticalRulerView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setVerticalRulerView:")
    ObjCRuntime.msgSend(null, ptr, sel, verticalRulerView)
}

// Class<*> method: +[NSScrollView rulerViewClass]
fun NSScrollView_rulerViewClass(): Class<*> {
    val sel = ObjCRuntime.sel("rulerViewClass")
    val cls = ObjCRuntime.getClass("NSScrollView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as Class<*>
}

// Class<*> method: +[NSScrollView setRulerViewClass:]
fun NSScrollView_setRulerViewClass(rulerViewClass: Class<*>): Unit {
    val sel = ObjCRuntime.sel("setRulerViewClass:")
    val cls = ObjCRuntime.getClass("NSScrollView")
    ObjCRuntime.msgSend(null, cls, sel, rulerViewClass)
}

// @property rulerViewClass
fun NSScrollView.rulerViewClass(): Class<*> {
    val sel = ObjCRuntime.sel("rulerViewClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
}
fun NSScrollView.setRulerViewClass(value: Class<*>) {
    val sel = ObjCRuntime.sel("setRulerViewClass:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property rulersVisible
fun NSScrollView.rulersVisible(): BOOL {
    val sel = ObjCRuntime.sel("rulersVisible")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSScrollView.setRulersVisible(value: BOOL) {
    val sel = ObjCRuntime.sel("setRulersVisible:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property hasHorizontalRuler
fun NSScrollView.hasHorizontalRuler(): BOOL {
    val sel = ObjCRuntime.sel("hasHorizontalRuler")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSScrollView.setHasHorizontalRuler(value: BOOL) {
    val sel = ObjCRuntime.sel("setHasHorizontalRuler:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property hasVerticalRuler
fun NSScrollView.hasVerticalRuler(): BOOL {
    val sel = ObjCRuntime.sel("hasVerticalRuler")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSScrollView.setHasVerticalRuler(value: BOOL) {
    val sel = ObjCRuntime.sel("setHasVerticalRuler:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property horizontalRulerView
fun NSScrollView.horizontalRulerView(): MemorySegment {
    val sel = ObjCRuntime.sel("horizontalRulerView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSScrollView.setHorizontalRulerView(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setHorizontalRulerView:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property verticalRulerView
fun NSScrollView.verticalRulerView(): MemorySegment {
    val sel = ObjCRuntime.sel("verticalRulerView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSScrollView.setVerticalRulerView(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setVerticalRulerView:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSFindBarSupport on NSScrollView ─────────────────────────────────────────

fun NSScrollView.findBarPosition(): NSScrollViewFindBarPosition {
    val sel = ObjCRuntime.sel("findBarPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollViewFindBarPosition
}

fun NSScrollView.setFindBarPosition(findBarPosition: NSScrollViewFindBarPosition): Unit {
    val sel = ObjCRuntime.sel("setFindBarPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, findBarPosition)
}

// @property findBarPosition
fun NSScrollView.findBarPosition(): NSScrollViewFindBarPosition {
    val sel = ObjCRuntime.sel("findBarPosition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSScrollViewFindBarPosition
}
fun NSScrollView.setFindBarPosition(value: NSScrollViewFindBarPosition) {
    val sel = ObjCRuntime.sel("setFindBarPosition:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

