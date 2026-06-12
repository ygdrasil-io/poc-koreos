package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSView
 * Superclass: NSResponder
 * Protocols: NSAnimatablePropertyContainer, NSUserInterfaceItemIdentification, NSDraggingDestination, NSAppearanceCustomization, NSAccessibilityElement, NSAccessibility
 */
open class NSView(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSView") }
        
        fun focusView(): MemorySegment {
            val sel = ObjCRuntime.sel("focusView")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun defaultMenu(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultMenu")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun isCompatibleWithResponsiveScrolling(): BOOL {
            val sel = ObjCRuntime.sel("isCompatibleWithResponsiveScrolling")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun initWithFrame(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun isDescendantOf(view: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDescendantOf:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, view) as BOOL
    }
    
    fun ancestorSharedWithView(view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("ancestorSharedWithView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view) as MemorySegment
    }
    
    fun getRectsBeingDrawn_count(rects: MemorySegment, count: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getRectsBeingDrawn:count:")
        ObjCRuntime.msgSend(null, ptr, sel, rects, count)
    }
    
    fun needsToDrawRect(rect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("needsToDrawRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    fun viewDidHide(): Unit {
        val sel = ObjCRuntime.sel("viewDidHide")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewDidUnhide(): Unit {
        val sel = ObjCRuntime.sel("viewDidUnhide")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addSubview(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSubview:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun addSubview_positioned_relativeTo(view: MemorySegment, place: NSWindowOrderingMode, otherView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSubview:positioned:relativeTo:")
        ObjCRuntime.msgSend(null, ptr, sel, view, place, otherView)
    }
    
    fun sortSubviewsUsingFunction_context(compare: MemorySegment, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("sortSubviewsUsingFunction:context:")
        ObjCRuntime.msgSend(null, ptr, sel, compare, context)
    }
    
    fun viewWillMoveToWindow(newWindow: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewWillMoveToWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, newWindow)
    }
    
    fun viewDidMoveToWindow(): Unit {
        val sel = ObjCRuntime.sel("viewDidMoveToWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewWillMoveToSuperview(newSuperview: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewWillMoveToSuperview:")
        ObjCRuntime.msgSend(null, ptr, sel, newSuperview)
    }
    
    fun viewDidMoveToSuperview(): Unit {
        val sel = ObjCRuntime.sel("viewDidMoveToSuperview")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun didAddSubview(subview: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("didAddSubview:")
        ObjCRuntime.msgSend(null, ptr, sel, subview)
    }
    
    fun willRemoveSubview(subview: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("willRemoveSubview:")
        ObjCRuntime.msgSend(null, ptr, sel, subview)
    }
    
    fun removeFromSuperview(): Unit {
        val sel = ObjCRuntime.sel("removeFromSuperview")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun replaceSubview_with(oldView: MemorySegment, newView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceSubview:with:")
        ObjCRuntime.msgSend(null, ptr, sel, oldView, newView)
    }
    
    fun removeFromSuperviewWithoutNeedingDisplay(): Unit {
        val sel = ObjCRuntime.sel("removeFromSuperviewWithoutNeedingDisplay")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewDidChangeBackingProperties(): Unit {
        val sel = ObjCRuntime.sel("viewDidChangeBackingProperties")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resizeSubviewsWithOldSize(oldSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("resizeSubviewsWithOldSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(oldSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun resizeWithOldSuperviewSize(oldSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("resizeWithOldSuperviewSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(oldSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun setFrameOrigin(newOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("setFrameOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun setFrameSize(newSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("setFrameSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun setBoundsOrigin(newOrigin: NSPoint): Unit {
        val sel = ObjCRuntime.sel("setBoundsOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun setBoundsSize(newSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("setBoundsSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun translateOriginToPoint(translation: NSPoint): Unit {
        val sel = ObjCRuntime.sel("translateOriginToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(translation, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun scaleUnitSquareToSize(newUnitSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("scaleUnitSquareToSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newUnitSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun rotateByAngle(angle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("rotateByAngle:")
        ObjCRuntime.msgSend(null, ptr, sel, angle)
    }
    
    fun convertPoint_fromView(point: NSPoint, view: MemorySegment): NSPoint {
        val sel = ObjCRuntime.sel("convertPoint:fromView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), view) as NSPoint
    }
    
    fun convertPoint_toView(point: NSPoint, view: MemorySegment): NSPoint {
        val sel = ObjCRuntime.sel("convertPoint:toView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), view) as NSPoint
    }
    
    fun convertSize_fromView(size: NSSize, view: MemorySegment): NSSize {
        val sel = ObjCRuntime.sel("convertSize:fromView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), view) as NSSize
    }
    
    fun convertSize_toView(size: NSSize, view: MemorySegment): NSSize {
        val sel = ObjCRuntime.sel("convertSize:toView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), view) as NSSize
    }
    
    fun convertRect_fromView(rect: NSRect, view: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("convertRect:fromView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as NSRect
    }
    
    fun convertRect_toView(rect: NSRect, view: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("convertRect:toView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as NSRect
    }
    
    fun backingAlignedRect_options(rect: NSRect, options: NSAlignmentOptions): NSRect {
        val sel = ObjCRuntime.sel("backingAlignedRect:options:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), options) as NSRect
    }
    
    fun centerScanRect(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("centerScanRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertPointToBacking(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointToBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertPointFromBacking(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointFromBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertSizeToBacking(size: NSSize): NSSize {
        val sel = ObjCRuntime.sel("convertSizeToBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    fun convertSizeFromBacking(size: NSSize): NSSize {
        val sel = ObjCRuntime.sel("convertSizeFromBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    fun convertRectToBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectToBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertRectFromBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectFromBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertPointToLayer(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointToLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertPointFromLayer(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointFromLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertSizeToLayer(size: NSSize): NSSize {
        val sel = ObjCRuntime.sel("convertSizeToLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    fun convertSizeFromLayer(size: NSSize): NSSize {
        val sel = ObjCRuntime.sel("convertSizeFromLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    fun convertRectToLayer(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectToLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertRectFromLayer(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectFromLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun setNeedsDisplayInRect(invalidRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("setNeedsDisplayInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(invalidRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun lockFocus(): Unit {
        val sel = ObjCRuntime.sel("lockFocus")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun unlockFocus(): Unit {
        val sel = ObjCRuntime.sel("unlockFocus")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun lockFocusIfCanDraw(): BOOL {
        val sel = ObjCRuntime.sel("lockFocusIfCanDraw")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun lockFocusIfCanDrawInContext(context: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("lockFocusIfCanDrawInContext:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, context) as BOOL
    }
    
    fun display(): Unit {
        val sel = ObjCRuntime.sel("display")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun displayIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("displayIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun displayIfNeededIgnoringOpacity(): Unit {
        val sel = ObjCRuntime.sel("displayIfNeededIgnoringOpacity")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun displayRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("displayRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun displayIfNeededInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("displayIfNeededInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun displayRectIgnoringOpacity(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("displayRectIgnoringOpacity:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun displayIfNeededInRectIgnoringOpacity(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("displayIfNeededInRectIgnoringOpacity:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun drawRect(dirtyRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(dirtyRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun displayRectIgnoringOpacity_inContext(rect: NSRect, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("displayRectIgnoringOpacity:inContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), context)
    }
    
    fun bitmapImageRepForCachingDisplayInRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("bitmapImageRepForCachingDisplayInRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun cacheDisplayInRect_toBitmapImageRep(rect: NSRect, bitmapImageRep: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cacheDisplayInRect:toBitmapImageRep:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), bitmapImageRep)
    }
    
    fun viewWillDraw(): Unit {
        val sel = ObjCRuntime.sel("viewWillDraw")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scrollPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("scrollPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun scrollRectToVisible(rect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("scrollRectToVisible:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    fun autoscroll(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("autoscroll:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun adjustScroll(newVisible: NSRect): NSRect {
        val sel = ObjCRuntime.sel("adjustScroll:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(newVisible, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun scrollRect_by(rect: NSRect, delta: NSSize): Unit {
        val sel = ObjCRuntime.sel("scrollRect:by:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(delta, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun translateRectsNeedingDisplayInRect_by(clipRect: NSRect, delta: NSSize): Unit {
        val sel = ObjCRuntime.sel("translateRectsNeedingDisplayInRect:by:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(clipRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(delta, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun hitTest(point: NSPoint): MemorySegment {
        val sel = ObjCRuntime.sel("hitTest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    fun mouse_inRect(point: NSPoint, rect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("mouse:inRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    fun viewWithTag(tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("viewWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tag) as MemorySegment
    }
    
    override fun `performKeyEquivalent`(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performKeyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun acceptsFirstMouse(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("acceptsFirstMouse:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun shouldDelayWindowOrderingForEvent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("shouldDelayWindowOrderingForEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    fun makeBackingLayer(): MemorySegment {
        val sel = ObjCRuntime.sel("makeBackingLayer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun updateLayer(): Unit {
        val sel = ObjCRuntime.sel("updateLayer")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun layoutSubtreeIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("layoutSubtreeIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun layout(): Unit {
        val sel = ObjCRuntime.sel("layout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun menuForEvent(event: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("menuForEvent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event) as MemorySegment
    }
    
    fun willOpenMenu_withEvent(menu: MemorySegment, event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("willOpenMenu:withEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, menu, event)
    }
    
    fun didCloseMenu_withEvent(menu: MemorySegment, event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("didCloseMenu:withEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, menu, event)
    }
    
    fun addToolTipRect_owner_userData(rect: NSRect, owner: MemorySegment, `data`: MemorySegment): NSToolTipTag {
        val sel = ObjCRuntime.sel("addToolTipRect:owner:userData:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), owner, `data`) as NSToolTipTag
    }
    
    fun removeToolTip(tag: NSToolTipTag): Unit {
        val sel = ObjCRuntime.sel("removeToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, tag)
    }
    
    fun removeAllToolTips(): Unit {
        val sel = ObjCRuntime.sel("removeAllToolTips")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewWillStartLiveResize(): Unit {
        val sel = ObjCRuntime.sel("viewWillStartLiveResize")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun viewDidEndLiveResize(): Unit {
        val sel = ObjCRuntime.sel("viewDidEndLiveResize")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun getRectsExposedDuringLiveResize_count(exposedRects: MemorySegment, count: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getRectsExposedDuringLiveResize:count:")
        ObjCRuntime.msgSend(null, ptr, sel, exposedRects, count)
    }
    
    fun rectForSmartMagnificationAtPoint_inRect(location: NSPoint, visibleRect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("rectForSmartMagnificationAtPoint:inRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(visibleRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun prepareForReuse(): Unit {
        val sel = ObjCRuntime.sel("prepareForReuse")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun prepareContentInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("prepareContentInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun viewDidChangeEffectiveAppearance(): Unit {
        val sel = ObjCRuntime.sel("viewDidChangeEffectiveAppearance")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property window
    fun window(): MemorySegment {
        val sel = ObjCRuntime.sel("window")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property superview
    fun superview(): MemorySegment {
        val sel = ObjCRuntime.sel("superview")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property subviews
    /** @return NSArray<__kindof NSView *> * */
    fun subviews(): MemorySegment {
        val sel = ObjCRuntime.sel("subviews")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubviews(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubviews:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opaqueAncestor
    fun opaqueAncestor(): MemorySegment {
        val sel = ObjCRuntime.sel("opaqueAncestor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hiddenOrHasHiddenAncestor
    fun isHiddenOrHasHiddenAncestor(): BOOL {
        val sel = ObjCRuntime.sel("isHiddenOrHasHiddenAncestor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property wantsDefaultClipping
    fun wantsDefaultClipping(): BOOL {
        val sel = ObjCRuntime.sel("wantsDefaultClipping")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property postsFrameChangedNotifications
    fun postsFrameChangedNotifications(): BOOL {
        val sel = ObjCRuntime.sel("postsFrameChangedNotifications")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPostsFrameChangedNotifications(value: BOOL) {
        val sel = ObjCRuntime.sel("setPostsFrameChangedNotifications:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoresizesSubviews
    fun autoresizesSubviews(): BOOL {
        val sel = ObjCRuntime.sel("autoresizesSubviews")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutoresizesSubviews(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutoresizesSubviews:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoresizingMask
    fun autoresizingMask(): NSAutoresizingMaskOptions {
        val sel = ObjCRuntime.sel("autoresizingMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAutoresizingMaskOptions
    }
    fun setAutoresizingMask(value: NSAutoresizingMaskOptions) {
        val sel = ObjCRuntime.sel("setAutoresizingMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property frameRotation
    fun frameRotation(): CGFloat {
        val sel = ObjCRuntime.sel("frameRotation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setFrameRotation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFrameRotation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frameCenterRotation
    fun frameCenterRotation(): CGFloat {
        val sel = ObjCRuntime.sel("frameCenterRotation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setFrameCenterRotation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFrameCenterRotation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property boundsRotation
    fun boundsRotation(): CGFloat {
        val sel = ObjCRuntime.sel("boundsRotation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setBoundsRotation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBoundsRotation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bounds
    fun bounds(): NSRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setBounds(value: NSRect) {
        val sel = ObjCRuntime.sel("setBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property flipped
    fun isFlipped(): BOOL {
        val sel = ObjCRuntime.sel("isFlipped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property rotatedFromBase
    fun isRotatedFromBase(): BOOL {
        val sel = ObjCRuntime.sel("isRotatedFromBase")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property rotatedOrScaledFromBase
    fun isRotatedOrScaledFromBase(): BOOL {
        val sel = ObjCRuntime.sel("isRotatedOrScaledFromBase")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property opaque
    fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canDrawConcurrently
    fun canDrawConcurrently(): BOOL {
        val sel = ObjCRuntime.sel("canDrawConcurrently")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanDrawConcurrently(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanDrawConcurrently:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canDraw
    fun canDraw(): BOOL {
        val sel = ObjCRuntime.sel("canDraw")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
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
    
    // @property focusView
    fun focusView(): MemorySegment {
        val sel = ObjCRuntime.sel("focusView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property visibleRect
    fun visibleRect(): NSRect {
        val sel = ObjCRuntime.sel("visibleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property tag
    fun tag(): NSInteger {
        val sel = ObjCRuntime.sel("tag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property needsPanelToBecomeKey
    fun needsPanelToBecomeKey(): BOOL {
        val sel = ObjCRuntime.sel("needsPanelToBecomeKey")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property mouseDownCanMoveWindow
    fun mouseDownCanMoveWindow(): BOOL {
        val sel = ObjCRuntime.sel("mouseDownCanMoveWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property acceptsTouchEvents
    fun acceptsTouchEvents(): BOOL {
        val sel = ObjCRuntime.sel("acceptsTouchEvents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAcceptsTouchEvents(value: BOOL) {
        val sel = ObjCRuntime.sel("setAcceptsTouchEvents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsRestingTouches
    fun wantsRestingTouches(): BOOL {
        val sel = ObjCRuntime.sel("wantsRestingTouches")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setWantsRestingTouches(value: BOOL) {
        val sel = ObjCRuntime.sel("setWantsRestingTouches:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layerContentsRedrawPolicy
    fun layerContentsRedrawPolicy(): NSViewLayerContentsRedrawPolicy {
        val sel = ObjCRuntime.sel("layerContentsRedrawPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSViewLayerContentsRedrawPolicy
    }
    fun setLayerContentsRedrawPolicy(value: NSViewLayerContentsRedrawPolicy) {
        val sel = ObjCRuntime.sel("setLayerContentsRedrawPolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layerContentsPlacement
    fun layerContentsPlacement(): NSViewLayerContentsPlacement {
        val sel = ObjCRuntime.sel("layerContentsPlacement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSViewLayerContentsPlacement
    }
    fun setLayerContentsPlacement(value: NSViewLayerContentsPlacement) {
        val sel = ObjCRuntime.sel("setLayerContentsPlacement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsLayer
    fun wantsLayer(): BOOL {
        val sel = ObjCRuntime.sel("wantsLayer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setWantsLayer(value: BOOL) {
        val sel = ObjCRuntime.sel("setWantsLayer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layer
    fun layer(): MemorySegment {
        val sel = ObjCRuntime.sel("layer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLayer(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLayer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsUpdateLayer
    fun wantsUpdateLayer(): BOOL {
        val sel = ObjCRuntime.sel("wantsUpdateLayer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canDrawSubviewsIntoLayer
    fun canDrawSubviewsIntoLayer(): BOOL {
        val sel = ObjCRuntime.sel("canDrawSubviewsIntoLayer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanDrawSubviewsIntoLayer(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanDrawSubviewsIntoLayer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property needsLayout
    fun needsLayout(): BOOL {
        val sel = ObjCRuntime.sel("needsLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNeedsLayout(value: BOOL) {
        val sel = ObjCRuntime.sel("setNeedsLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alphaValue
    fun alphaValue(): CGFloat {
        val sel = ObjCRuntime.sel("alphaValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setAlphaValue(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAlphaValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layerUsesCoreImageFilters
    fun layerUsesCoreImageFilters(): BOOL {
        val sel = ObjCRuntime.sel("layerUsesCoreImageFilters")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLayerUsesCoreImageFilters(value: BOOL) {
        val sel = ObjCRuntime.sel("setLayerUsesCoreImageFilters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundFilters
    /** @return NSArray<__kindof CIFilter *> * */
    fun backgroundFilters(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundFilters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundFilters(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundFilters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property compositingFilter
    fun compositingFilter(): MemorySegment {
        val sel = ObjCRuntime.sel("compositingFilter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCompositingFilter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCompositingFilter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentFilters
    /** @return NSArray<__kindof CIFilter *> * */
    fun contentFilters(): MemorySegment {
        val sel = ObjCRuntime.sel("contentFilters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentFilters(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentFilters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadow
    fun shadow(): MemorySegment {
        val sel = ObjCRuntime.sel("shadow")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShadow(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShadow:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property clipsToBounds
    fun clipsToBounds(): BOOL {
        val sel = ObjCRuntime.sel("clipsToBounds")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setClipsToBounds(value: BOOL) {
        val sel = ObjCRuntime.sel("setClipsToBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property postsBoundsChangedNotifications
    fun postsBoundsChangedNotifications(): BOOL {
        val sel = ObjCRuntime.sel("postsBoundsChangedNotifications")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPostsBoundsChangedNotifications(value: BOOL) {
        val sel = ObjCRuntime.sel("setPostsBoundsChangedNotifications:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enclosingScrollView
    fun enclosingScrollView(): MemorySegment {
        val sel = ObjCRuntime.sel("enclosingScrollView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaultMenu
    fun defaultMenu(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultMenu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property toolTip
    fun toolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("toolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setToolTip(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun toolTipAsString(): String = ObjCRuntime.toJavaString(toolTip())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setToolTip(value: String) = setToolTip(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property inLiveResize
    fun inLiveResize(): BOOL {
        val sel = ObjCRuntime.sel("inLiveResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property preservesContentDuringLiveResize
    fun preservesContentDuringLiveResize(): BOOL {
        val sel = ObjCRuntime.sel("preservesContentDuringLiveResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property rectPreservedDuringLiveResize
    fun rectPreservedDuringLiveResize(): NSRect {
        val sel = ObjCRuntime.sel("rectPreservedDuringLiveResize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property inputContext
    fun inputContext(): MemorySegment {
        val sel = ObjCRuntime.sel("inputContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property userInterfaceLayoutDirection
    fun userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    fun setUserInterfaceLayoutDirection(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property compatibleWithResponsiveScrolling
    fun isCompatibleWithResponsiveScrolling(): BOOL {
        val sel = ObjCRuntime.sel("isCompatibleWithResponsiveScrolling")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property preparedContentRect
    fun preparedContentRect(): NSRect {
        val sel = ObjCRuntime.sel("preparedContentRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setPreparedContentRect(value: NSRect) {
        val sel = ObjCRuntime.sel("setPreparedContentRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property allowsVibrancy
    fun allowsVibrancy(): BOOL {
        val sel = ObjCRuntime.sel("allowsVibrancy")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSKeyboardUI on NSView ─────────────────────────────────────────

fun NSView.setKeyboardFocusRingNeedsDisplayInRect(rect: NSRect): Unit {
    val sel = ObjCRuntime.sel("setKeyboardFocusRingNeedsDisplayInRect:")
    ObjCRuntime.msgSend(null, ptr, sel, rect)
}

fun NSView.drawFocusRingMask(): Unit {
    val sel = ObjCRuntime.sel("drawFocusRingMask")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.noteFocusRingMaskChanged(): Unit {
    val sel = ObjCRuntime.sel("noteFocusRingMaskChanged")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.nextKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("nextKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.setNextKeyView(nextKeyView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setNextKeyView:")
    ObjCRuntime.msgSend(null, ptr, sel, nextKeyView)
}

fun NSView.previousKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("previousKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.nextValidKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("nextValidKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.previousValidKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("previousValidKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.canBecomeKeyView(): BOOL {
    val sel = ObjCRuntime.sel("canBecomeKeyView")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.focusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("focusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}

fun NSView.setFocusRingType(focusRingType: NSFocusRingType): Unit {
    val sel = ObjCRuntime.sel("setFocusRingType:")
    ObjCRuntime.msgSend(null, ptr, sel, focusRingType)
}

fun NSView.focusRingMaskBounds(): NSRect {
    val sel = ObjCRuntime.sel("focusRingMaskBounds")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

// Class<*> method: +[NSView defaultFocusRingType]
fun NSView_defaultFocusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("defaultFocusRingType")
    val cls = ObjCRuntime.getClass("NSView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as NSFocusRingType
}

// @property nextKeyView
fun NSView.nextKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("nextKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSView.setNextKeyView(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setNextKeyView:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property previousKeyView
fun NSView.previousKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("previousKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property nextValidKeyView
fun NSView.nextValidKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("nextValidKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property previousValidKeyView
fun NSView.previousValidKeyView(): MemorySegment {
    val sel = ObjCRuntime.sel("previousValidKeyView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property canBecomeKeyView
fun NSView.canBecomeKeyView(): BOOL {
    val sel = ObjCRuntime.sel("canBecomeKeyView")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property focusRingType
fun NSView.focusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("focusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}
fun NSView.setFocusRingType(value: NSFocusRingType) {
    val sel = ObjCRuntime.sel("setFocusRingType:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property defaultFocusRingType
fun NSView.defaultFocusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("defaultFocusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}

// @property focusRingMaskBounds
fun NSView.focusRingMaskBounds(): NSRect {
    val sel = ObjCRuntime.sel("focusRingMaskBounds")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

// ── Category: NSPrinting on NSView ─────────────────────────────────────────

fun NSView.writeEPSInsideRect_toPasteboard(rect: NSRect, pasteboard: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("writeEPSInsideRect:toPasteboard:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, pasteboard)
}

fun NSView.dataWithEPSInsideRect(rect: NSRect): MemorySegment {
    val sel = ObjCRuntime.sel("dataWithEPSInsideRect:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rect) as MemorySegment
}

fun NSView.writePDFInsideRect_toPasteboard(rect: NSRect, pasteboard: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("writePDFInsideRect:toPasteboard:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, pasteboard)
}

fun NSView.dataWithPDFInsideRect(rect: NSRect): MemorySegment {
    val sel = ObjCRuntime.sel("dataWithPDFInsideRect:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rect) as MemorySegment
}

fun NSView.print(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("print:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSView.knowsPageRange(range: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("knowsPageRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, range) as BOOL
}

fun NSView.adjustPageWidthNew_left_right_limit(newRight: MemorySegment, oldLeft: CGFloat, oldRight: CGFloat, rightLimit: CGFloat): Unit {
    val sel = ObjCRuntime.sel("adjustPageWidthNew:left:right:limit:")
    ObjCRuntime.msgSend(null, ptr, sel, newRight, oldLeft, oldRight, rightLimit)
}

fun NSView.adjustPageHeightNew_top_bottom_limit(newBottom: MemorySegment, oldTop: CGFloat, oldBottom: CGFloat, bottomLimit: CGFloat): Unit {
    val sel = ObjCRuntime.sel("adjustPageHeightNew:top:bottom:limit:")
    ObjCRuntime.msgSend(null, ptr, sel, newBottom, oldTop, oldBottom, bottomLimit)
}

fun NSView.rectForPage(page: NSInteger): NSRect {
    val sel = ObjCRuntime.sel("rectForPage:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, page) as NSRect
}

fun NSView.locationOfPrintRect(rect: NSRect): NSPoint {
    val sel = ObjCRuntime.sel("locationOfPrintRect:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, rect) as NSPoint
}

fun NSView.drawPageBorderWithSize(borderSize: NSSize): Unit {
    val sel = ObjCRuntime.sel("drawPageBorderWithSize:")
    ObjCRuntime.msgSend(null, ptr, sel, borderSize)
}

fun NSView.drawSheetBorderWithSize(borderSize: NSSize): Unit {
    val sel = ObjCRuntime.sel("drawSheetBorderWithSize:")
    ObjCRuntime.msgSend(null, ptr, sel, borderSize)
}

fun NSView.beginDocument(): Unit {
    val sel = ObjCRuntime.sel("beginDocument")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.endDocument(): Unit {
    val sel = ObjCRuntime.sel("endDocument")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.beginPageInRect_atPlacement(rect: NSRect, location: NSPoint): Unit {
    val sel = ObjCRuntime.sel("beginPageInRect:atPlacement:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, location)
}

fun NSView.endPage(): Unit {
    val sel = ObjCRuntime.sel("endPage")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.heightAdjustLimit(): CGFloat {
    val sel = ObjCRuntime.sel("heightAdjustLimit")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSView.widthAdjustLimit(): CGFloat {
    val sel = ObjCRuntime.sel("widthAdjustLimit")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSView.pageHeader(): MemorySegment {
    val sel = ObjCRuntime.sel("pageHeader")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.pageFooter(): MemorySegment {
    val sel = ObjCRuntime.sel("pageFooter")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.printJobTitle(): MemorySegment {
    val sel = ObjCRuntime.sel("printJobTitle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property heightAdjustLimit
fun NSView.heightAdjustLimit(): CGFloat {
    val sel = ObjCRuntime.sel("heightAdjustLimit")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property widthAdjustLimit
fun NSView.widthAdjustLimit(): CGFloat {
    val sel = ObjCRuntime.sel("widthAdjustLimit")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property pageHeader
fun NSView.pageHeader(): MemorySegment {
    val sel = ObjCRuntime.sel("pageHeader")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property pageFooter
fun NSView.pageFooter(): MemorySegment {
    val sel = ObjCRuntime.sel("pageFooter")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property printJobTitle
fun NSView.printJobTitle(): MemorySegment {
    val sel = ObjCRuntime.sel("printJobTitle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSDrag on NSView ─────────────────────────────────────────

fun NSView.beginDraggingSessionWithItems_event_source(items: MemorySegment, event: MemorySegment, source: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("beginDraggingSessionWithItems:event:source:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, items, event, source) as MemorySegment
}

fun NSView.registerForDraggedTypes(newTypes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerForDraggedTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, newTypes)
}

fun NSView.unregisterDraggedTypes(): Unit {
    val sel = ObjCRuntime.sel("unregisterDraggedTypes")
    ObjCRuntime.msgSend(null, ptr, sel)
}

/** @return NSArray<NSPasteboardType> * */
fun NSView.registeredDraggedTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("registeredDraggedTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property registeredDraggedTypes
/** @return NSArray<NSPasteboardType> * */
fun NSView.registeredDraggedTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("registeredDraggedTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSFullScreenMode on NSView ─────────────────────────────────────────

fun NSView.enterFullScreenMode_withOptions(screen: MemorySegment, options: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("enterFullScreenMode:withOptions:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, screen, options) as BOOL
}

fun NSView.exitFullScreenModeWithOptions(options: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("exitFullScreenModeWithOptions:")
    ObjCRuntime.msgSend(null, ptr, sel, options)
}

fun NSView.isInFullScreenMode(): BOOL {
    val sel = ObjCRuntime.sel("isInFullScreenMode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property inFullScreenMode
fun NSView.isInFullScreenMode(): BOOL {
    val sel = ObjCRuntime.sel("isInFullScreenMode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSDefinition on NSView ─────────────────────────────────────────

fun NSView.showDefinitionForAttributedString_atPoint(attrString: MemorySegment, textBaselineOrigin: NSPoint): Unit {
    val sel = ObjCRuntime.sel("showDefinitionForAttributedString:atPoint:")
    ObjCRuntime.msgSend(null, ptr, sel, attrString, textBaselineOrigin)
}

fun NSView.showDefinitionForAttributedString_range_options_baselineOriginProvider(attrString: MemorySegment, targetRange: NSRange, options: MemorySegment, originProvider: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("showDefinitionForAttributedString:range:options:baselineOriginProvider:")
    ObjCRuntime.msgSend(null, ptr, sel, attrString, targetRange, options, originProvider)
}

// ── Category: NSFindIndicator on NSView ─────────────────────────────────────────

fun NSView.isDrawingFindIndicator(): BOOL {
    val sel = ObjCRuntime.sel("isDrawingFindIndicator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property drawingFindIndicator
fun NSView.isDrawingFindIndicator(): BOOL {
    val sel = ObjCRuntime.sel("isDrawingFindIndicator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSGestureRecognizer on NSView ─────────────────────────────────────────

fun NSView.addGestureRecognizer(gestureRecognizer: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addGestureRecognizer:")
    ObjCRuntime.msgSend(null, ptr, sel, gestureRecognizer)
}

fun NSView.removeGestureRecognizer(gestureRecognizer: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeGestureRecognizer:")
    ObjCRuntime.msgSend(null, ptr, sel, gestureRecognizer)
}

/** @return NSArray<__kindof NSGestureRecognizer *> * */
fun NSView.gestureRecognizers(): MemorySegment {
    val sel = ObjCRuntime.sel("gestureRecognizers")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.setGestureRecognizers(gestureRecognizers: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setGestureRecognizers:")
    ObjCRuntime.msgSend(null, ptr, sel, gestureRecognizers)
}

// @property gestureRecognizers
/** @return NSArray<__kindof NSGestureRecognizer *> * */
fun NSView.gestureRecognizers(): MemorySegment {
    val sel = ObjCRuntime.sel("gestureRecognizers")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSView.setGestureRecognizers(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setGestureRecognizers:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSTouchBar on NSView ─────────────────────────────────────────

fun NSView.allowedTouchTypes(): NSTouchTypeMask {
    val sel = ObjCRuntime.sel("allowedTouchTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchTypeMask
}

fun NSView.setAllowedTouchTypes(allowedTouchTypes: NSTouchTypeMask): Unit {
    val sel = ObjCRuntime.sel("setAllowedTouchTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, allowedTouchTypes)
}

// @property allowedTouchTypes
fun NSView.allowedTouchTypes(): NSTouchTypeMask {
    val sel = ObjCRuntime.sel("allowedTouchTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTouchTypeMask
}
fun NSView.setAllowedTouchTypes(value: NSTouchTypeMask) {
    val sel = ObjCRuntime.sel("setAllowedTouchTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSSafeAreas on NSView ─────────────────────────────────────────

fun NSView.safeAreaInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("safeAreaInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}

fun NSView.additionalSafeAreaInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("additionalSafeAreaInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}

fun NSView.setAdditionalSafeAreaInsets(additionalSafeAreaInsets: NSEdgeInsets): Unit {
    val sel = ObjCRuntime.sel("setAdditionalSafeAreaInsets:")
    ObjCRuntime.msgSend(null, ptr, sel, additionalSafeAreaInsets)
}

fun NSView.safeAreaLayoutGuide(): MemorySegment {
    val sel = ObjCRuntime.sel("safeAreaLayoutGuide")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.safeAreaRect(): NSRect {
    val sel = ObjCRuntime.sel("safeAreaRect")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

fun NSView.layoutMarginsGuide(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutMarginsGuide")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property safeAreaInsets
fun NSView.safeAreaInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("safeAreaInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}

// @property additionalSafeAreaInsets
fun NSView.additionalSafeAreaInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("additionalSafeAreaInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}
fun NSView.setAdditionalSafeAreaInsets(value: NSEdgeInsets) {
    val sel = ObjCRuntime.sel("setAdditionalSafeAreaInsets:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property safeAreaLayoutGuide
fun NSView.safeAreaLayoutGuide(): MemorySegment {
    val sel = ObjCRuntime.sel("safeAreaLayoutGuide")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property safeAreaRect
fun NSView.safeAreaRect(): NSRect {
    val sel = ObjCRuntime.sel("safeAreaRect")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

// @property layoutMarginsGuide
fun NSView.layoutMarginsGuide(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutMarginsGuide")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSCompactControlSizeMetrics on NSView ─────────────────────────────────────────

fun NSView.prefersCompactControlSizeMetrics(): BOOL {
    val sel = ObjCRuntime.sel("prefersCompactControlSizeMetrics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setPrefersCompactControlSizeMetrics(prefersCompactControlSizeMetrics: BOOL): Unit {
    val sel = ObjCRuntime.sel("setPrefersCompactControlSizeMetrics:")
    ObjCRuntime.msgSend(null, ptr, sel, prefersCompactControlSizeMetrics)
}

// @property prefersCompactControlSizeMetrics
fun NSView.prefersCompactControlSizeMetrics(): BOOL {
    val sel = ObjCRuntime.sel("prefersCompactControlSizeMetrics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setPrefersCompactControlSizeMetrics(value: BOOL) {
    val sel = ObjCRuntime.sel("setPrefersCompactControlSizeMetrics:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSTrackingArea on NSView ─────────────────────────────────────────

fun NSView.addTrackingArea(trackingArea: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addTrackingArea:")
    ObjCRuntime.msgSend(null, ptr, sel, trackingArea)
}

fun NSView.removeTrackingArea(trackingArea: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeTrackingArea:")
    ObjCRuntime.msgSend(null, ptr, sel, trackingArea)
}

fun NSView.updateTrackingAreas(): Unit {
    val sel = ObjCRuntime.sel("updateTrackingAreas")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.addCursorRect_cursor(rect: NSRect, `object`: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addCursorRect:cursor:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, `object`)
}

fun NSView.removeCursorRect_cursor(rect: NSRect, `object`: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeCursorRect:cursor:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, `object`)
}

fun NSView.discardCursorRects(): Unit {
    val sel = ObjCRuntime.sel("discardCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.resetCursorRects(): Unit {
    val sel = ObjCRuntime.sel("resetCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.addTrackingRect_owner_userData_assumeInside(rect: NSRect, owner: MemorySegment, `data`: MemorySegment, flag: BOOL): NSTrackingRectTag {
    val sel = ObjCRuntime.sel("addTrackingRect:owner:userData:assumeInside:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, rect, owner, `data`, flag) as NSTrackingRectTag
}

fun NSView.removeTrackingRect(tag: NSTrackingRectTag): Unit {
    val sel = ObjCRuntime.sel("removeTrackingRect:")
    ObjCRuntime.msgSend(null, ptr, sel, tag)
}

/** @return NSArray<NSTrackingArea *> * */
fun NSView.trackingAreas(): MemorySegment {
    val sel = ObjCRuntime.sel("trackingAreas")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property trackingAreas
/** @return NSArray<NSTrackingArea *> * */
fun NSView.trackingAreas(): MemorySegment {
    val sel = ObjCRuntime.sel("trackingAreas")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSDisplayLink on NSView ─────────────────────────────────────────

fun NSView.displayLinkWithTarget_selector(target: MemorySegment, selector: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("displayLinkWithTarget:selector:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, selector) as MemorySegment
}

// ── Category: NSDeprecated on NSView ─────────────────────────────────────────

fun NSView.dragImage_at_offset_event_pasteboard_source_slideBack(image: MemorySegment, viewLocation: NSPoint, initialOffset: NSSize, event: MemorySegment, pboard: MemorySegment, sourceObj: MemorySegment, slideFlag: BOOL): Unit {
    val sel = ObjCRuntime.sel("dragImage:at:offset:event:pasteboard:source:slideBack:")
    ObjCRuntime.msgSend(null, ptr, sel, image, viewLocation, initialOffset, event, pboard, sourceObj, slideFlag)
}

fun NSView.dragFile_fromRect_slideBack_event(filename: MemorySegment, rect: NSRect, flag: BOOL, event: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("dragFile:fromRect:slideBack:event:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, filename, rect, flag, event) as BOOL
}

fun NSView.dragPromisedFilesOfTypes_fromRect_source_slideBack_event(typeArray: MemorySegment, rect: NSRect, sourceObject: MemorySegment, flag: BOOL, event: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("dragPromisedFilesOfTypes:fromRect:source:slideBack:event:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, typeArray, rect, sourceObject, flag, event) as BOOL
}

fun NSView.convertPointToBase(point: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("convertPointToBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, point) as NSPoint
}

fun NSView.convertPointFromBase(point: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("convertPointFromBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, point) as NSPoint
}

fun NSView.convertSizeToBase(size: NSSize): NSSize {
    val sel = ObjCRuntime.sel("convertSizeToBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, size) as NSSize
}

fun NSView.convertSizeFromBase(size: NSSize): NSSize {
    val sel = ObjCRuntime.sel("convertSizeFromBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, size) as NSSize
}

fun NSView.convertRectToBase(rect: NSRect): NSRect {
    val sel = ObjCRuntime.sel("convertRectToBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, rect) as NSRect
}

fun NSView.convertRectFromBase(rect: NSRect): NSRect {
    val sel = ObjCRuntime.sel("convertRectFromBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, rect) as NSRect
}

fun NSView.performMnemonic(string: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("performMnemonic:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
}

fun NSView.shouldDrawColor(): BOOL {
    val sel = ObjCRuntime.sel("shouldDrawColor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.gState(): NSInteger {
    val sel = ObjCRuntime.sel("gState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSView.allocateGState(): Unit {
    val sel = ObjCRuntime.sel("allocateGState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.releaseGState(): Unit {
    val sel = ObjCRuntime.sel("releaseGState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.setUpGState(): Unit {
    val sel = ObjCRuntime.sel("setUpGState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.renewGState(): Unit {
    val sel = ObjCRuntime.sel("renewGState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSWritingToolsCoordinator on NSView ─────────────────────────────────────────

fun NSView.writingToolsCoordinator(): MemorySegment {
    val sel = ObjCRuntime.sel("writingToolsCoordinator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.setWritingToolsCoordinator(writingToolsCoordinator: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setWritingToolsCoordinator:")
    ObjCRuntime.msgSend(null, ptr, sel, writingToolsCoordinator)
}

// @property writingToolsCoordinator
fun NSView.writingToolsCoordinator(): MemorySegment {
    val sel = ObjCRuntime.sel("writingToolsCoordinator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSView.setWritingToolsCoordinator(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setWritingToolsCoordinator:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSViewEnclosingMenuItem on NSView ─────────────────────────────────────────

fun NSView.enclosingMenuItem(): MemorySegment {
    val sel = ObjCRuntime.sel("enclosingMenuItem")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property enclosingMenuItem
fun NSView.enclosingMenuItem(): MemorySegment {
    val sel = ObjCRuntime.sel("enclosingMenuItem")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSCandidateListTouchBarItem on NSView ─────────────────────────────────────────

fun NSView.candidateListTouchBarItem(): MemorySegment {
    val sel = ObjCRuntime.sel("candidateListTouchBarItem")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property candidateListTouchBarItem
fun NSView.candidateListTouchBarItem(): MemorySegment {
    val sel = ObjCRuntime.sel("candidateListTouchBarItem")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSClipViewSuperview on NSView ─────────────────────────────────────────

fun NSView.reflectScrolledClipView(clipView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("reflectScrolledClipView:")
    ObjCRuntime.msgSend(null, ptr, sel, clipView)
}

fun NSView.scrollClipView_toPoint(clipView: MemorySegment, point: NSPoint): Unit {
    val sel = ObjCRuntime.sel("scrollClipView:toPoint:")
    ObjCRuntime.msgSend(null, ptr, sel, clipView, point)
}

// ── Category: NSConstraintBasedLayoutInstallingConstraints on NSView ─────────────────────────────────────────

fun NSView.addConstraint(constraint: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addConstraint:")
    ObjCRuntime.msgSend(null, ptr, sel, constraint)
}

fun NSView.addConstraints(constraints: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, constraints)
}

fun NSView.removeConstraint(constraint: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeConstraint:")
    ObjCRuntime.msgSend(null, ptr, sel, constraint)
}

fun NSView.removeConstraints(constraints: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, constraints)
}

fun NSView.leadingAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("leadingAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.trailingAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("trailingAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.leftAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("leftAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.rightAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("rightAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.topAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("topAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.bottomAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("bottomAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.widthAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("widthAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.heightAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("heightAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.centerXAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("centerXAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.centerYAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("centerYAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.firstBaselineAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("firstBaselineAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.lastBaselineAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("lastBaselineAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSLayoutConstraint *> * */
fun NSView.constraints(): MemorySegment {
    val sel = ObjCRuntime.sel("constraints")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property leadingAnchor
fun NSView.leadingAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("leadingAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property trailingAnchor
fun NSView.trailingAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("trailingAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property leftAnchor
fun NSView.leftAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("leftAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property rightAnchor
fun NSView.rightAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("rightAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property topAnchor
fun NSView.topAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("topAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property bottomAnchor
fun NSView.bottomAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("bottomAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property widthAnchor
fun NSView.widthAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("widthAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property heightAnchor
fun NSView.heightAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("heightAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property centerXAnchor
fun NSView.centerXAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("centerXAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property centerYAnchor
fun NSView.centerYAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("centerYAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property firstBaselineAnchor
fun NSView.firstBaselineAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("firstBaselineAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property lastBaselineAnchor
fun NSView.lastBaselineAnchor(): MemorySegment {
    val sel = ObjCRuntime.sel("lastBaselineAnchor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property constraints
/** @return NSArray<NSLayoutConstraint *> * */
fun NSView.constraints(): MemorySegment {
    val sel = ObjCRuntime.sel("constraints")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSConstraintBasedLayoutCoreMethods on NSView ─────────────────────────────────────────

fun NSView.updateConstraintsForSubtreeIfNeeded(): Unit {
    val sel = ObjCRuntime.sel("updateConstraintsForSubtreeIfNeeded")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.updateConstraints(): Unit {
    val sel = ObjCRuntime.sel("updateConstraints")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.needsUpdateConstraints(): BOOL {
    val sel = ObjCRuntime.sel("needsUpdateConstraints")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setNeedsUpdateConstraints(needsUpdateConstraints: BOOL): Unit {
    val sel = ObjCRuntime.sel("setNeedsUpdateConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, needsUpdateConstraints)
}

// @property needsUpdateConstraints
fun NSView.needsUpdateConstraints(): BOOL {
    val sel = ObjCRuntime.sel("needsUpdateConstraints")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setNeedsUpdateConstraints(value: BOOL) {
    val sel = ObjCRuntime.sel("setNeedsUpdateConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSConstraintBasedCompatibility on NSView ─────────────────────────────────────────

fun NSView.translatesAutoresizingMaskIntoConstraints(): BOOL {
    val sel = ObjCRuntime.sel("translatesAutoresizingMaskIntoConstraints")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setTranslatesAutoresizingMaskIntoConstraints(translatesAutoresizingMaskIntoConstraints: BOOL): Unit {
    val sel = ObjCRuntime.sel("setTranslatesAutoresizingMaskIntoConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, translatesAutoresizingMaskIntoConstraints)
}

// Class<*> method: +[NSView requiresConstraintBasedLayout]
fun NSView_requiresConstraintBasedLayout(): BOOL {
    val sel = ObjCRuntime.sel("requiresConstraintBasedLayout")
    val cls = ObjCRuntime.getClass("NSView")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel) as BOOL
}

// @property translatesAutoresizingMaskIntoConstraints
fun NSView.translatesAutoresizingMaskIntoConstraints(): BOOL {
    val sel = ObjCRuntime.sel("translatesAutoresizingMaskIntoConstraints")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setTranslatesAutoresizingMaskIntoConstraints(value: BOOL) {
    val sel = ObjCRuntime.sel("setTranslatesAutoresizingMaskIntoConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property requiresConstraintBasedLayout
fun NSView.requiresConstraintBasedLayout(): BOOL {
    val sel = ObjCRuntime.sel("requiresConstraintBasedLayout")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSConstraintBasedLayoutLayering on NSView ─────────────────────────────────────────

fun NSView.alignmentRectForFrame(frame: NSRect): NSRect {
    val sel = ObjCRuntime.sel("alignmentRectForFrame:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, frame) as NSRect
}

fun NSView.frameForAlignmentRect(alignmentRect: NSRect): NSRect {
    val sel = ObjCRuntime.sel("frameForAlignmentRect:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, alignmentRect) as NSRect
}

fun NSView.invalidateIntrinsicContentSize(): Unit {
    val sel = ObjCRuntime.sel("invalidateIntrinsicContentSize")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.contentHuggingPriorityForOrientation(orientation: NSLayoutConstraintOrientation): NSLayoutPriority {
    val sel = ObjCRuntime.sel("contentHuggingPriorityForOrientation:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, orientation) as NSLayoutPriority
}

fun NSView.setContentHuggingPriority_forOrientation(priority: NSLayoutPriority, orientation: NSLayoutConstraintOrientation): Unit {
    val sel = ObjCRuntime.sel("setContentHuggingPriority:forOrientation:")
    ObjCRuntime.msgSend(null, ptr, sel, priority, orientation)
}

fun NSView.contentCompressionResistancePriorityForOrientation(orientation: NSLayoutConstraintOrientation): NSLayoutPriority {
    val sel = ObjCRuntime.sel("contentCompressionResistancePriorityForOrientation:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, orientation) as NSLayoutPriority
}

fun NSView.setContentCompressionResistancePriority_forOrientation(priority: NSLayoutPriority, orientation: NSLayoutConstraintOrientation): Unit {
    val sel = ObjCRuntime.sel("setContentCompressionResistancePriority:forOrientation:")
    ObjCRuntime.msgSend(null, ptr, sel, priority, orientation)
}

fun NSView.alignmentRectInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("alignmentRectInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}

fun NSView.firstBaselineOffsetFromTop(): CGFloat {
    val sel = ObjCRuntime.sel("firstBaselineOffsetFromTop")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSView.lastBaselineOffsetFromBottom(): CGFloat {
    val sel = ObjCRuntime.sel("lastBaselineOffsetFromBottom")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSView.baselineOffsetFromBottom(): CGFloat {
    val sel = ObjCRuntime.sel("baselineOffsetFromBottom")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSView.intrinsicContentSize(): NSSize {
    val sel = ObjCRuntime.sel("intrinsicContentSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

fun NSView.isHorizontalContentSizeConstraintActive(): BOOL {
    val sel = ObjCRuntime.sel("isHorizontalContentSizeConstraintActive")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setHorizontalContentSizeConstraintActive(horizontalContentSizeConstraintActive: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHorizontalContentSizeConstraintActive:")
    ObjCRuntime.msgSend(null, ptr, sel, horizontalContentSizeConstraintActive)
}

fun NSView.isVerticalContentSizeConstraintActive(): BOOL {
    val sel = ObjCRuntime.sel("isVerticalContentSizeConstraintActive")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setVerticalContentSizeConstraintActive(verticalContentSizeConstraintActive: BOOL): Unit {
    val sel = ObjCRuntime.sel("setVerticalContentSizeConstraintActive:")
    ObjCRuntime.msgSend(null, ptr, sel, verticalContentSizeConstraintActive)
}

// @property alignmentRectInsets
fun NSView.alignmentRectInsets(): NSEdgeInsets {
    val sel = ObjCRuntime.sel("alignmentRectInsets")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
}

// @property firstBaselineOffsetFromTop
fun NSView.firstBaselineOffsetFromTop(): CGFloat {
    val sel = ObjCRuntime.sel("firstBaselineOffsetFromTop")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property lastBaselineOffsetFromBottom
fun NSView.lastBaselineOffsetFromBottom(): CGFloat {
    val sel = ObjCRuntime.sel("lastBaselineOffsetFromBottom")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property baselineOffsetFromBottom
fun NSView.baselineOffsetFromBottom(): CGFloat {
    val sel = ObjCRuntime.sel("baselineOffsetFromBottom")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

// @property intrinsicContentSize
fun NSView.intrinsicContentSize(): NSSize {
    val sel = ObjCRuntime.sel("intrinsicContentSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// @property horizontalContentSizeConstraintActive
fun NSView.isHorizontalContentSizeConstraintActive(): BOOL {
    val sel = ObjCRuntime.sel("isHorizontalContentSizeConstraintActive")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setHorizontalContentSizeConstraintActive(value: BOOL) {
    val sel = ObjCRuntime.sel("setHorizontalContentSizeConstraintActive:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property verticalContentSizeConstraintActive
fun NSView.isVerticalContentSizeConstraintActive(): BOOL {
    val sel = ObjCRuntime.sel("isVerticalContentSizeConstraintActive")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setVerticalContentSizeConstraintActive(value: BOOL) {
    val sel = ObjCRuntime.sel("setVerticalContentSizeConstraintActive:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSConstraintBasedLayoutFittingSize on NSView ─────────────────────────────────────────

fun NSView.fittingSize(): NSSize {
    val sel = ObjCRuntime.sel("fittingSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// @property fittingSize
fun NSView.fittingSize(): NSSize {
    val sel = ObjCRuntime.sel("fittingSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// ── Category: NSConstraintBasedLayoutDebugging on NSView ─────────────────────────────────────────

/** @return NSArray<NSLayoutConstraint *> * */
fun NSView.constraintsAffectingLayoutForOrientation(orientation: NSLayoutConstraintOrientation): MemorySegment {
    val sel = ObjCRuntime.sel("constraintsAffectingLayoutForOrientation:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, orientation) as MemorySegment
}

fun NSView.exerciseAmbiguityInLayout(): Unit {
    val sel = ObjCRuntime.sel("exerciseAmbiguityInLayout")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSView.hasAmbiguousLayout(): BOOL {
    val sel = ObjCRuntime.sel("hasAmbiguousLayout")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property hasAmbiguousLayout
fun NSView.hasAmbiguousLayout(): BOOL {
    val sel = ObjCRuntime.sel("hasAmbiguousLayout")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSLayoutGuideSupport on NSView ─────────────────────────────────────────

fun NSView.addLayoutGuide(guide: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addLayoutGuide:")
    ObjCRuntime.msgSend(null, ptr, sel, guide)
}

fun NSView.removeLayoutGuide(guide: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeLayoutGuide:")
    ObjCRuntime.msgSend(null, ptr, sel, guide)
}

/** @return NSArray<NSLayoutGuide *> * */
fun NSView.layoutGuides(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutGuides")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property layoutGuides
/** @return NSArray<NSLayoutGuide *> * */
fun NSView.layoutGuides(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutGuides")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: LayoutRegions on NSView ─────────────────────────────────────────

fun NSView.layoutGuideForLayoutRegion(layoutRegion: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("layoutGuideForLayoutRegion:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, layoutRegion) as MemorySegment
}

fun NSView.edgeInsetsForLayoutRegion(layoutRegion: MemorySegment): NSEdgeInsets {
    val sel = ObjCRuntime.sel("edgeInsetsForLayoutRegion:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel, layoutRegion) as NSEdgeInsets
}

fun NSView.rectForLayoutRegion(layoutRegion: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("rectForLayoutRegion:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, layoutRegion) as NSRect
}

// ── Category: NSRulerMarkerClientViewDelegation on NSView ─────────────────────────────────────────

fun NSView.rulerView_shouldMoveMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("rulerView:shouldMoveMarker:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
}

fun NSView.rulerView_willMoveMarker_toLocation(ruler: MemorySegment, marker: MemorySegment, location: CGFloat): CGFloat {
    val sel = ObjCRuntime.sel("rulerView:willMoveMarker:toLocation:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ruler, marker, location) as CGFloat
}

fun NSView.rulerView_didMoveMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rulerView:didMoveMarker:")
    ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
}

fun NSView.rulerView_shouldRemoveMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("rulerView:shouldRemoveMarker:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
}

fun NSView.rulerView_didRemoveMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rulerView:didRemoveMarker:")
    ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
}

fun NSView.rulerView_shouldAddMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("rulerView:shouldAddMarker:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
}

fun NSView.rulerView_willAddMarker_atLocation(ruler: MemorySegment, marker: MemorySegment, location: CGFloat): CGFloat {
    val sel = ObjCRuntime.sel("rulerView:willAddMarker:atLocation:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ruler, marker, location) as CGFloat
}

fun NSView.rulerView_didAddMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rulerView:didAddMarker:")
    ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
}

fun NSView.rulerView_handleMouseDown(ruler: MemorySegment, event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rulerView:handleMouseDown:")
    ObjCRuntime.msgSend(null, ptr, sel, ruler, event)
}

fun NSView.rulerView_willSetClientView(ruler: MemorySegment, newClient: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("rulerView:willSetClientView:")
    ObjCRuntime.msgSend(null, ptr, sel, ruler, newClient)
}

fun NSView.rulerView_locationForPoint(ruler: MemorySegment, point: NSPoint): CGFloat {
    val sel = ObjCRuntime.sel("rulerView:locationForPoint:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ruler, point) as CGFloat
}

fun NSView.rulerView_pointForLocation(ruler: MemorySegment, point: CGFloat): NSPoint {
    val sel = ObjCRuntime.sel("rulerView:pointForLocation:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ruler, point) as NSPoint
}

// ── Category: NSOpenGLSurfaceResolution on NSView ─────────────────────────────────────────

fun NSView.wantsBestResolutionOpenGLSurface(): BOOL {
    val sel = ObjCRuntime.sel("wantsBestResolutionOpenGLSurface")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setWantsBestResolutionOpenGLSurface(wantsBestResolutionOpenGLSurface: BOOL): Unit {
    val sel = ObjCRuntime.sel("setWantsBestResolutionOpenGLSurface:")
    ObjCRuntime.msgSend(null, ptr, sel, wantsBestResolutionOpenGLSurface)
}

// @property wantsBestResolutionOpenGLSurface
fun NSView.wantsBestResolutionOpenGLSurface(): BOOL {
    val sel = ObjCRuntime.sel("wantsBestResolutionOpenGLSurface")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setWantsBestResolutionOpenGLSurface(value: BOOL) {
    val sel = ObjCRuntime.sel("setWantsBestResolutionOpenGLSurface:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSExtendedDynamicRange on NSView ─────────────────────────────────────────

fun NSView.wantsExtendedDynamicRangeOpenGLSurface(): BOOL {
    val sel = ObjCRuntime.sel("wantsExtendedDynamicRangeOpenGLSurface")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSView.setWantsExtendedDynamicRangeOpenGLSurface(wantsExtendedDynamicRangeOpenGLSurface: BOOL): Unit {
    val sel = ObjCRuntime.sel("setWantsExtendedDynamicRangeOpenGLSurface:")
    ObjCRuntime.msgSend(null, ptr, sel, wantsExtendedDynamicRangeOpenGLSurface)
}

// @property wantsExtendedDynamicRangeOpenGLSurface
fun NSView.wantsExtendedDynamicRangeOpenGLSurface(): BOOL {
    val sel = ObjCRuntime.sel("wantsExtendedDynamicRangeOpenGLSurface")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSView.setWantsExtendedDynamicRangeOpenGLSurface(value: BOOL) {
    val sel = ObjCRuntime.sel("setWantsExtendedDynamicRangeOpenGLSurface:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSPressureConfiguration on NSView ─────────────────────────────────────────

fun NSView.pressureConfiguration(): MemorySegment {
    val sel = ObjCRuntime.sel("pressureConfiguration")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSView.setPressureConfiguration(pressureConfiguration: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setPressureConfiguration:")
    ObjCRuntime.msgSend(null, ptr, sel, pressureConfiguration)
}

// @property pressureConfiguration
fun NSView.pressureConfiguration(): MemorySegment {
    val sel = ObjCRuntime.sel("pressureConfiguration")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSView.setPressureConfiguration(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setPressureConfiguration:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

