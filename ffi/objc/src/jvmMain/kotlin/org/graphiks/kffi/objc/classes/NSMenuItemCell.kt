package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMenuItemCell
 * Superclass: NSButtonCell
 */
open class NSMenuItemCell(ptr: MemorySegment) : NSButtonCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMenuItemCell") }
        
    }
    
    override fun `initTextCell`(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `initTextCell`(string: String): MemorySegment = initTextCell(ObjCRuntime.newNSString(Arena.global(), string))
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun calcSize(): Unit {
        val sel = ObjCRuntime.sel("calcSize")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stateImageRectForBounds(cellFrame: NSRect): NSRect {
        val sel = ObjCRuntime.sel("stateImageRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun titleRectForBounds(cellFrame: NSRect): NSRect {
        val sel = ObjCRuntime.sel("titleRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun keyEquivalentRectForBounds(cellFrame: NSRect): NSRect {
        val sel = ObjCRuntime.sel("keyEquivalentRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun drawSeparatorItemWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawSeparatorItemWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawStateImageWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawStateImageWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawImageWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawImageWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawTitleWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawTitleWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawKeyEquivalentWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawKeyEquivalentWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    fun drawBorderAndBackgroundWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawBorderAndBackgroundWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    // @property menuItem
    fun menuItem(): MemorySegment {
        val sel = ObjCRuntime.sel("menuItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenuItem(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenuItem:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property needsSizing
    fun needsSizing(): BOOL {
        val sel = ObjCRuntime.sel("needsSizing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNeedsSizing(value: BOOL) {
        val sel = ObjCRuntime.sel("setNeedsSizing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property needsDisplay
    fun needsDisplay(): BOOL {
        val sel = ObjCRuntime.sel("needsDisplay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNeedsDisplay(value: BOOL) {
        val sel = ObjCRuntime.sel("setNeedsDisplay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property stateImageWidth
    fun stateImageWidth(): CGFloat {
        val sel = ObjCRuntime.sel("stateImageWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property imageWidth
    fun imageWidth(): CGFloat {
        val sel = ObjCRuntime.sel("imageWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property titleWidth
    fun titleWidth(): CGFloat {
        val sel = ObjCRuntime.sel("titleWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property keyEquivalentWidth
    fun keyEquivalentWidth(): CGFloat {
        val sel = ObjCRuntime.sel("keyEquivalentWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property tag
    fun tag(): NSInteger {
        val sel = ObjCRuntime.sel("tag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setTag(value: NSInteger) {
        val sel = ObjCRuntime.sel("setTag:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

