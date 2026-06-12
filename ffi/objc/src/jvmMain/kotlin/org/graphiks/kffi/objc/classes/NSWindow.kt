package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSWindow
 * Superclass: NSResponder
 * Protocols: NSAnimatablePropertyContainer, NSMenuItemValidation, NSUserInterfaceValidations, NSUserInterfaceItemIdentification, NSAppearanceCustomization, NSAccessibilityElement, NSAccessibility
 */
open class NSWindow(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWindow") }
        
        fun frameRectForContentRect_styleMask(cRect: NSRect, style: NSWindowStyleMask): NSRect {
            val sel = ObjCRuntime.sel("frameRectForContentRect:styleMask:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), _class, sel, ObjCRuntime.ObjCStructArg(cRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), style) as NSRect
        }
        
        fun contentRectForFrameRect_styleMask(fRect: NSRect, style: NSWindowStyleMask): NSRect {
            val sel = ObjCRuntime.sel("contentRectForFrameRect:styleMask:")
            return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), _class, sel, ObjCRuntime.ObjCStructArg(fRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), style) as NSRect
        }
        
        fun minFrameWidthWithTitle_styleMask(title: MemorySegment, style: NSWindowStyleMask): CGFloat {
            val sel = ObjCRuntime.sel("minFrameWidthWithTitle:styleMask:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel, title, style) as CGFloat
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun minFrameWidthWithTitle_styleMask(title: String, style: NSWindowStyleMask): CGFloat = minFrameWidthWithTitle_styleMask(ObjCRuntime.newNSString(Arena.global(), title), style)
        
        fun removeFrameUsingName(name: NSWindowFrameAutosaveName): Unit {
            val sel = ObjCRuntime.sel("removeFrameUsingName:")
            ObjCRuntime.msgSend(null, _class, sel, name)
        }
        
        fun standardWindowButton_forStyleMask(b: NSWindowButton, styleMask: NSWindowStyleMask): MemorySegment {
            val sel = ObjCRuntime.sel("standardWindowButton:forStyleMask:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, b, styleMask) as MemorySegment
        }
        
        /** @return NSArray<NSNumber *> * */
        fun windowNumbersWithOptions(options: NSWindowNumberListOptions): MemorySegment {
            val sel = ObjCRuntime.sel("windowNumbersWithOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, options) as MemorySegment
        }
        
        fun windowNumberAtPoint_belowWindowWithWindowNumber(point: NSPoint, windowNumber: NSInteger): NSInteger {
            val sel = ObjCRuntime.sel("windowNumberAtPoint:belowWindowWithWindowNumber:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, _class, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), windowNumber) as NSInteger
        }
        
        fun windowWithContentViewController(contentViewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("windowWithContentViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, contentViewController) as MemorySegment
        }
        
        fun defaultDepthLimit(): NSWindowDepth {
            val sel = ObjCRuntime.sel("defaultDepthLimit")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSWindowDepth
        }
        
        fun allowsAutomaticWindowTabbing(): BOOL {
            val sel = ObjCRuntime.sel("allowsAutomaticWindowTabbing")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun setAllowsAutomaticWindowTabbing(allowsAutomaticWindowTabbing: BOOL): Unit {
            val sel = ObjCRuntime.sel("setAllowsAutomaticWindowTabbing:")
            ObjCRuntime.msgSend(null, _class, sel, allowsAutomaticWindowTabbing)
        }
        
        fun userTabbingPreference(): NSWindowUserTabbingPreference {
            val sel = ObjCRuntime.sel("userTabbingPreference")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSWindowUserTabbingPreference
        }
        
    }
    
    fun frameRectForContentRect(contentRect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("frameRectForContentRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(contentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun contentRectForFrameRect(frameRect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("contentRectForFrameRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun initWithContentRect_styleMask_backing_defer(contentRect: NSRect, style: NSWindowStyleMask, backingStoreType: NSBackingStoreType, flag: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentRect:styleMask:backing:defer:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(contentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), style, backingStoreType, flag) as MemorySegment
    }
    
    fun initWithContentRect_styleMask_backing_defer_screen(contentRect: NSRect, style: NSWindowStyleMask, backingStoreType: NSBackingStoreType, flag: BOOL, screen: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentRect:styleMask:backing:defer:screen:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(contentRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), style, backingStoreType, flag, screen) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun addTitlebarAccessoryViewController(childViewController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTitlebarAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController)
    }
    
    fun insertTitlebarAccessoryViewController_atIndex(childViewController: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertTitlebarAccessoryViewController:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController, index)
    }
    
    fun removeTitlebarAccessoryViewControllerAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeTitlebarAccessoryViewControllerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun setTitleWithRepresentedFilename(filename: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTitleWithRepresentedFilename:")
        ObjCRuntime.msgSend(null, ptr, sel, filename)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setTitleWithRepresentedFilename(filename: String): Unit = setTitleWithRepresentedFilename(ObjCRuntime.newNSString(Arena.global(), filename))
    
    fun fieldEditor_forObject(createFlag: BOOL, `object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fieldEditor:forObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, createFlag, `object`) as MemorySegment
    }
    
    fun endEditingFor(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("endEditingFor:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun constrainFrameRect_toScreen(frameRect: NSRect, screen: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("constrainFrameRect:toScreen:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), screen) as NSRect
    }
    
    fun setFrame_display(frameRect: NSRect, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setFrame:display:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), flag)
    }
    
    fun setContentSize(size: NSSize): Unit {
        val sel = ObjCRuntime.sel("setContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun setFrameOrigin(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("setFrameOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun setFrameTopLeftPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("setFrameTopLeftPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun cascadeTopLeftFromPoint(topLeftPoint: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("cascadeTopLeftFromPoint:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(topLeftPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun animationResizeTime(newFrame: NSRect): NSTimeInterval {
        val sel = ObjCRuntime.sel("animationResizeTime:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ObjCRuntime.ObjCStructArg(newFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSTimeInterval
    }
    
    fun setFrame_display_animate(frameRect: NSRect, displayFlag: BOOL, animateFlag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setFrame:display:animate:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), displayFlag, animateFlag)
    }
    
    fun displayIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("displayIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun display(): Unit {
        val sel = ObjCRuntime.sel("display")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun update(): Unit {
        val sel = ObjCRuntime.sel("update")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makeFirstResponder(responder: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("makeFirstResponder:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, responder) as BOOL
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun miniaturize(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("miniaturize:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun deminiaturize(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deminiaturize:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun zoom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("zoom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    override fun `tryToPerform_with`(action: MemorySegment, `object`: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("tryToPerform:with:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, action, `object`) as BOOL
    }
    
    override fun `validRequestorForSendType_returnType`(sendType: NSPasteboardType, returnType: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("validRequestorForSendType:returnType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendType, returnType) as MemorySegment
    }
    
    fun setContentBorderThickness_forEdge(thickness: CGFloat, edge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("setContentBorderThickness:forEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, thickness, edge)
    }
    
    fun contentBorderThicknessForEdge(edge: NSRectEdge): CGFloat {
        val sel = ObjCRuntime.sel("contentBorderThicknessForEdge:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, edge) as CGFloat
    }
    
    fun setAutorecalculatesContentBorderThickness_forEdge(flag: BOOL, edge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("setAutorecalculatesContentBorderThickness:forEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, edge)
    }
    
    fun autorecalculatesContentBorderThicknessForEdge(edge: NSRectEdge): BOOL {
        val sel = ObjCRuntime.sel("autorecalculatesContentBorderThicknessForEdge:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, edge) as BOOL
    }
    
    fun center(): Unit {
        val sel = ObjCRuntime.sel("center")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makeKeyAndOrderFront(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("makeKeyAndOrderFront:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFront(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFront:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderBack(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderBack:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderOut(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderOut:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderWindow_relativeTo(place: NSWindowOrderingMode, otherWin: NSInteger): Unit {
        val sel = ObjCRuntime.sel("orderWindow:relativeTo:")
        ObjCRuntime.msgSend(null, ptr, sel, place, otherWin)
    }
    
    fun orderFrontRegardless(): Unit {
        val sel = ObjCRuntime.sel("orderFrontRegardless")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makeKeyWindow(): Unit {
        val sel = ObjCRuntime.sel("makeKeyWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makeMainWindow(): Unit {
        val sel = ObjCRuntime.sel("makeMainWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun becomeKeyWindow(): Unit {
        val sel = ObjCRuntime.sel("becomeKeyWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resignKeyWindow(): Unit {
        val sel = ObjCRuntime.sel("resignKeyWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun becomeMainWindow(): Unit {
        val sel = ObjCRuntime.sel("becomeMainWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resignMainWindow(): Unit {
        val sel = ObjCRuntime.sel("resignMainWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun convertRectToScreen(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectToScreen:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertRectFromScreen(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectFromScreen:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertPointToScreen(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointToScreen:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertPointFromScreen(point: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("convertPointFromScreen:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    fun convertRectToBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectToBacking:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun convertRectFromBacking(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("convertRectFromBacking:")
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
    
    fun backingAlignedRect_options(rect: NSRect, options: NSAlignmentOptions): NSRect {
        val sel = ObjCRuntime.sel("backingAlignedRect:options:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), options) as NSRect
    }
    
    fun performClose(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performClose:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun performMiniaturize(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performMiniaturize:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun performZoom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performZoom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun dataWithEPSInsideRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("dataWithEPSInsideRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun dataWithPDFInsideRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("dataWithPDFInsideRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun print(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("print:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun setDynamicDepthLimit(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDynamicDepthLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun invalidateShadow(): Unit {
        val sel = ObjCRuntime.sel("invalidateShadow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun toggleFullScreen(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleFullScreen:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun setFrameFromString(string: NSWindowPersistableFrameDescriptor): Unit {
        val sel = ObjCRuntime.sel("setFrameFromString:")
        ObjCRuntime.msgSend(null, ptr, sel, string)
    }
    
    fun saveFrameUsingName(name: NSWindowFrameAutosaveName): Unit {
        val sel = ObjCRuntime.sel("saveFrameUsingName:")
        ObjCRuntime.msgSend(null, ptr, sel, name)
    }
    
    fun setFrameUsingName_force(name: NSWindowFrameAutosaveName, force: BOOL): BOOL {
        val sel = ObjCRuntime.sel("setFrameUsingName:force:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name, force) as BOOL
    }
    
    fun setFrameUsingName(name: NSWindowFrameAutosaveName): BOOL {
        val sel = ObjCRuntime.sel("setFrameUsingName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name) as BOOL
    }
    
    fun setFrameAutosaveName(name: NSWindowFrameAutosaveName): BOOL {
        val sel = ObjCRuntime.sel("setFrameAutosaveName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name) as BOOL
    }
    
    fun beginSheet_completionHandler(sheetWindow: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheet:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, sheetWindow, handler)
    }
    
    fun beginCriticalSheet_completionHandler(sheetWindow: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginCriticalSheet:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, sheetWindow, handler)
    }
    
    fun endSheet(sheetWindow: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("endSheet:")
        ObjCRuntime.msgSend(null, ptr, sel, sheetWindow)
    }
    
    fun endSheet_returnCode(sheetWindow: MemorySegment, returnCode: NSModalResponse): Unit {
        val sel = ObjCRuntime.sel("endSheet:returnCode:")
        ObjCRuntime.msgSend(null, ptr, sel, sheetWindow, returnCode)
    }
    
    fun standardWindowButton(b: NSWindowButton): MemorySegment {
        val sel = ObjCRuntime.sel("standardWindowButton:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, b) as MemorySegment
    }
    
    fun addChildWindow_ordered(childWin: MemorySegment, place: NSWindowOrderingMode): Unit {
        val sel = ObjCRuntime.sel("addChildWindow:ordered:")
        ObjCRuntime.msgSend(null, ptr, sel, childWin, place)
    }
    
    fun removeChildWindow(childWin: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeChildWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, childWin)
    }
    
    fun canRepresentDisplayGamut(displayGamut: NSDisplayGamut): BOOL {
        val sel = ObjCRuntime.sel("canRepresentDisplayGamut:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, displayGamut) as BOOL
    }
    
    fun performWindowDragWithEvent(event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performWindowDragWithEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, event)
    }
    
    fun selectNextKeyView(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectNextKeyView:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectPreviousKeyView(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectPreviousKeyView:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectKeyViewFollowingView(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectKeyViewFollowingView:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun selectKeyViewPrecedingView(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectKeyViewPrecedingView:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun disableKeyEquivalentForDefaultButtonCell(): Unit {
        val sel = ObjCRuntime.sel("disableKeyEquivalentForDefaultButtonCell")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun enableKeyEquivalentForDefaultButtonCell(): Unit {
        val sel = ObjCRuntime.sel("enableKeyEquivalentForDefaultButtonCell")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun recalculateKeyViewLoop(): Unit {
        val sel = ObjCRuntime.sel("recalculateKeyViewLoop")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun toggleToolbarShown(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleToolbarShown:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun runToolbarCustomizationPalette(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("runToolbarCustomizationPalette:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectNextTab(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectNextTab:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectPreviousTab(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectPreviousTab:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun moveTabToNewWindow(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveTabToNewWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun mergeAllWindows(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mergeAllWindows:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun toggleTabBar(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleTabBar:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun toggleTabOverview(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleTabOverview:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun addTabbedWindow_ordered(window: MemorySegment, ordered: NSWindowOrderingMode): Unit {
        val sel = ObjCRuntime.sel("addTabbedWindow:ordered:")
        ObjCRuntime.msgSend(null, ptr, sel, window, ordered)
    }
    
    fun transferWindowSharingToWindow_completionHandler(window: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("transferWindowSharingToWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, window, completionHandler)
    }
    
    fun requestSharingOfWindow_completionHandler(window: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("requestSharingOfWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, window, completionHandler)
    }
    
    fun requestSharingOfWindowUsingPreview_title_completionHandler(image: MemorySegment, title: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("requestSharingOfWindowUsingPreview:title:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, image, title, completionHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun requestSharingOfWindowUsingPreview_title_completionHandler(image: MemorySegment, title: String, completionHandler: MemorySegment): Unit = requestSharingOfWindowUsingPreview_title_completionHandler(image, ObjCRuntime.newNSString(Arena.global(), title), completionHandler)
    
    // @property defaultDepthLimit
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property subtitle
    fun subtitle(): MemorySegment {
        val sel = ObjCRuntime.sel("subtitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubtitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubtitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun subtitleAsString(): String = ObjCRuntime.toJavaString(subtitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSubtitle(value: String) = setSubtitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property titleVisibility
    fun titleVisibility(): NSWindowTitleVisibility {
        val sel = ObjCRuntime.sel("titleVisibility")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowTitleVisibility
    }
    fun setTitleVisibility(value: NSWindowTitleVisibility) {
        val sel = ObjCRuntime.sel("setTitleVisibility:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titlebarAppearsTransparent
    fun titlebarAppearsTransparent(): BOOL {
        val sel = ObjCRuntime.sel("titlebarAppearsTransparent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTitlebarAppearsTransparent(value: BOOL) {
        val sel = ObjCRuntime.sel("setTitlebarAppearsTransparent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property toolbarStyle
    fun toolbarStyle(): NSWindowToolbarStyle {
        val sel = ObjCRuntime.sel("toolbarStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowToolbarStyle
    }
    fun setToolbarStyle(value: NSWindowToolbarStyle) {
        val sel = ObjCRuntime.sel("setToolbarStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentLayoutRect
    fun contentLayoutRect(): NSRect {
        val sel = ObjCRuntime.sel("contentLayoutRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property contentLayoutGuide
    fun contentLayoutGuide(): MemorySegment {
        val sel = ObjCRuntime.sel("contentLayoutGuide")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property titlebarAccessoryViewControllers
    /** @return NSArray<__kindof NSTitlebarAccessoryViewController *> * */
    fun titlebarAccessoryViewControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("titlebarAccessoryViewControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitlebarAccessoryViewControllers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitlebarAccessoryViewControllers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property representedURL
    fun representedURL(): MemorySegment {
        val sel = ObjCRuntime.sel("representedURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRepresentedURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRepresentedURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property representedFilename
    fun representedFilename(): MemorySegment {
        val sel = ObjCRuntime.sel("representedFilename")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRepresentedFilename(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRepresentedFilename:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun representedFilenameAsString(): String = ObjCRuntime.toJavaString(representedFilename())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setRepresentedFilename(value: String) = setRepresentedFilename(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property excludedFromWindowsMenu
    fun isExcludedFromWindowsMenu(): BOOL {
        val sel = ObjCRuntime.sel("isExcludedFromWindowsMenu")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setExcludedFromWindowsMenu(value: BOOL) {
        val sel = ObjCRuntime.sel("setExcludedFromWindowsMenu:")
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
    
    // @property delegate
    /** @return id<NSWindowDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property windowNumber
    fun windowNumber(): NSInteger {
        val sel = ObjCRuntime.sel("windowNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property styleMask
    fun styleMask(): NSWindowStyleMask {
        val sel = ObjCRuntime.sel("styleMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowStyleMask
    }
    fun setStyleMask(value: NSWindowStyleMask) {
        val sel = ObjCRuntime.sel("setStyleMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cascadingReferenceFrame
    fun cascadingReferenceFrame(): NSRect {
        val sel = ObjCRuntime.sel("cascadingReferenceFrame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property inLiveResize
    fun inLiveResize(): BOOL {
        val sel = ObjCRuntime.sel("inLiveResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resizeIncrements
    fun resizeIncrements(): NSSize {
        val sel = ObjCRuntime.sel("resizeIncrements")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setResizeIncrements(value: NSSize) {
        val sel = ObjCRuntime.sel("setResizeIncrements:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property aspectRatio
    fun aspectRatio(): NSSize {
        val sel = ObjCRuntime.sel("aspectRatio")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setAspectRatio(value: NSSize) {
        val sel = ObjCRuntime.sel("setAspectRatio:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property contentResizeIncrements
    fun contentResizeIncrements(): NSSize {
        val sel = ObjCRuntime.sel("contentResizeIncrements")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentResizeIncrements(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentResizeIncrements:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property contentAspectRatio
    fun contentAspectRatio(): NSSize {
        val sel = ObjCRuntime.sel("contentAspectRatio")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentAspectRatio(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentAspectRatio:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property viewsNeedDisplay
    fun viewsNeedDisplay(): BOOL {
        val sel = ObjCRuntime.sel("viewsNeedDisplay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setViewsNeedDisplay(value: BOOL) {
        val sel = ObjCRuntime.sel("setViewsNeedDisplay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preservesContentDuringLiveResize
    fun preservesContentDuringLiveResize(): BOOL {
        val sel = ObjCRuntime.sel("preservesContentDuringLiveResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPreservesContentDuringLiveResize(value: BOOL) {
        val sel = ObjCRuntime.sel("setPreservesContentDuringLiveResize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property firstResponder
    fun firstResponder(): MemorySegment {
        val sel = ObjCRuntime.sel("firstResponder")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property resizeFlags
    fun resizeFlags(): NSEventModifierFlags {
        val sel = ObjCRuntime.sel("resizeFlags")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventModifierFlags
    }
    
    // @property releasedWhenClosed
    fun isReleasedWhenClosed(): BOOL {
        val sel = ObjCRuntime.sel("isReleasedWhenClosed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setReleasedWhenClosed(value: BOOL) {
        val sel = ObjCRuntime.sel("setReleasedWhenClosed:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property zoomed
    fun isZoomed(): BOOL {
        val sel = ObjCRuntime.sel("isZoomed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property miniaturized
    fun isMiniaturized(): BOOL {
        val sel = ObjCRuntime.sel("isMiniaturized")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
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
    
    // @property movable
    fun isMovable(): BOOL {
        val sel = ObjCRuntime.sel("isMovable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setMovable(value: BOOL) {
        val sel = ObjCRuntime.sel("setMovable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property movableByWindowBackground
    fun isMovableByWindowBackground(): BOOL {
        val sel = ObjCRuntime.sel("isMovableByWindowBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setMovableByWindowBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setMovableByWindowBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidesOnDeactivate
    fun hidesOnDeactivate(): BOOL {
        val sel = ObjCRuntime.sel("hidesOnDeactivate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidesOnDeactivate(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidesOnDeactivate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canHide
    fun canHide(): BOOL {
        val sel = ObjCRuntime.sel("canHide")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanHide(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanHide:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property miniwindowImage
    fun miniwindowImage(): MemorySegment {
        val sel = ObjCRuntime.sel("miniwindowImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMiniwindowImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMiniwindowImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property miniwindowTitle
    fun miniwindowTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("miniwindowTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMiniwindowTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMiniwindowTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun miniwindowTitleAsString(): String = ObjCRuntime.toJavaString(miniwindowTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMiniwindowTitle(value: String) = setMiniwindowTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property dockTile
    fun dockTile(): MemorySegment {
        val sel = ObjCRuntime.sel("dockTile")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property documentEdited
    fun isDocumentEdited(): BOOL {
        val sel = ObjCRuntime.sel("isDocumentEdited")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDocumentEdited(value: BOOL) {
        val sel = ObjCRuntime.sel("setDocumentEdited:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property keyWindow
    fun isKeyWindow(): BOOL {
        val sel = ObjCRuntime.sel("isKeyWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property mainWindow
    fun isMainWindow(): BOOL {
        val sel = ObjCRuntime.sel("isMainWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canBecomeKeyWindow
    fun canBecomeKeyWindow(): BOOL {
        val sel = ObjCRuntime.sel("canBecomeKeyWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canBecomeMainWindow
    fun canBecomeMainWindow(): BOOL {
        val sel = ObjCRuntime.sel("canBecomeMainWindow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property worksWhenModal
    fun worksWhenModal(): BOOL {
        val sel = ObjCRuntime.sel("worksWhenModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property preventsApplicationTerminationWhenModal
    fun preventsApplicationTerminationWhenModal(): BOOL {
        val sel = ObjCRuntime.sel("preventsApplicationTerminationWhenModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPreventsApplicationTerminationWhenModal(value: BOOL) {
        val sel = ObjCRuntime.sel("setPreventsApplicationTerminationWhenModal:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backingScaleFactor
    fun backingScaleFactor(): CGFloat {
        val sel = ObjCRuntime.sel("backingScaleFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property allowsToolTipsWhenApplicationIsInactive
    fun allowsToolTipsWhenApplicationIsInactive(): BOOL {
        val sel = ObjCRuntime.sel("allowsToolTipsWhenApplicationIsInactive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsToolTipsWhenApplicationIsInactive(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsToolTipsWhenApplicationIsInactive:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backingType
    fun backingType(): NSBackingStoreType {
        val sel = ObjCRuntime.sel("backingType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackingStoreType
    }
    fun setBackingType(value: NSBackingStoreType) {
        val sel = ObjCRuntime.sel("setBackingType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property level
    fun level(): NSWindowLevel {
        val sel = ObjCRuntime.sel("level")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSWindowLevel
    }
    fun setLevel(value: NSWindowLevel) {
        val sel = ObjCRuntime.sel("setLevel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property depthLimit
    fun depthLimit(): NSWindowDepth {
        val sel = ObjCRuntime.sel("depthLimit")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowDepth
    }
    fun setDepthLimit(value: NSWindowDepth) {
        val sel = ObjCRuntime.sel("setDepthLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasDynamicDepthLimit
    fun hasDynamicDepthLimit(): BOOL {
        val sel = ObjCRuntime.sel("hasDynamicDepthLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property screen
    fun screen(): MemorySegment {
        val sel = ObjCRuntime.sel("screen")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deepestScreen
    fun deepestScreen(): MemorySegment {
        val sel = ObjCRuntime.sel("deepestScreen")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hasShadow
    fun hasShadow(): BOOL {
        val sel = ObjCRuntime.sel("hasShadow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHasShadow(value: BOOL) {
        val sel = ObjCRuntime.sel("setHasShadow:")
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
    
    // @property opaque
    fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setOpaque(value: BOOL) {
        val sel = ObjCRuntime.sel("setOpaque:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sharingType
    fun sharingType(): NSWindowSharingType {
        val sel = ObjCRuntime.sel("sharingType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowSharingType
    }
    fun setSharingType(value: NSWindowSharingType) {
        val sel = ObjCRuntime.sel("setSharingType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsConcurrentViewDrawing
    fun allowsConcurrentViewDrawing(): BOOL {
        val sel = ObjCRuntime.sel("allowsConcurrentViewDrawing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsConcurrentViewDrawing(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsConcurrentViewDrawing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property displaysWhenScreenProfileChanges
    fun displaysWhenScreenProfileChanges(): BOOL {
        val sel = ObjCRuntime.sel("displaysWhenScreenProfileChanges")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDisplaysWhenScreenProfileChanges(value: BOOL) {
        val sel = ObjCRuntime.sel("setDisplaysWhenScreenProfileChanges:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canBecomeVisibleWithoutLogin
    fun canBecomeVisibleWithoutLogin(): BOOL {
        val sel = ObjCRuntime.sel("canBecomeVisibleWithoutLogin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanBecomeVisibleWithoutLogin(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanBecomeVisibleWithoutLogin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collectionBehavior
    fun collectionBehavior(): NSWindowCollectionBehavior {
        val sel = ObjCRuntime.sel("collectionBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowCollectionBehavior
    }
    fun setCollectionBehavior(value: NSWindowCollectionBehavior) {
        val sel = ObjCRuntime.sel("setCollectionBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animationBehavior
    fun animationBehavior(): NSWindowAnimationBehavior {
        val sel = ObjCRuntime.sel("animationBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowAnimationBehavior
    }
    fun setAnimationBehavior(value: NSWindowAnimationBehavior) {
        val sel = ObjCRuntime.sel("setAnimationBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property onActiveSpace
    fun isOnActiveSpace(): BOOL {
        val sel = ObjCRuntime.sel("isOnActiveSpace")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property stringWithSavedFrame
    fun stringWithSavedFrame(): NSWindowPersistableFrameDescriptor {
        val sel = ObjCRuntime.sel("stringWithSavedFrame")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowPersistableFrameDescriptor
    }
    
    // @property frameAutosaveName
    fun frameAutosaveName(): NSWindowFrameAutosaveName {
        val sel = ObjCRuntime.sel("frameAutosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowFrameAutosaveName
    }
    
    // @property minSize
    fun minSize(): NSSize {
        val sel = ObjCRuntime.sel("minSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property maxSize
    fun maxSize(): NSSize {
        val sel = ObjCRuntime.sel("maxSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMaxSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMaxSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property contentMinSize
    fun contentMinSize(): NSSize {
        val sel = ObjCRuntime.sel("contentMinSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentMinSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentMinSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property contentMaxSize
    fun contentMaxSize(): NSSize {
        val sel = ObjCRuntime.sel("contentMaxSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setContentMaxSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setContentMaxSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property minFullScreenContentSize
    fun minFullScreenContentSize(): NSSize {
        val sel = ObjCRuntime.sel("minFullScreenContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinFullScreenContentSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinFullScreenContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property maxFullScreenContentSize
    fun maxFullScreenContentSize(): NSSize {
        val sel = ObjCRuntime.sel("maxFullScreenContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMaxFullScreenContentSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMaxFullScreenContentSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property deviceDescription
    /** @return NSDictionary<NSDeviceDescriptionKey,id> * */
    fun deviceDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windowController
    fun windowController(): MemorySegment {
        val sel = ObjCRuntime.sel("windowController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setWindowController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setWindowController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sheets
    /** @return NSArray<__kindof NSWindow *> * */
    fun sheets(): MemorySegment {
        val sel = ObjCRuntime.sel("sheets")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property attachedSheet
    fun attachedSheet(): MemorySegment {
        val sel = ObjCRuntime.sel("attachedSheet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sheet
    fun isSheet(): BOOL {
        val sel = ObjCRuntime.sel("isSheet")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property sheetParent
    fun sheetParent(): MemorySegment {
        val sel = ObjCRuntime.sel("sheetParent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property childWindows
    /** @return NSArray<__kindof NSWindow *> * */
    fun childWindows(): MemorySegment {
        val sel = ObjCRuntime.sel("childWindows")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property appearanceSource
    /** @return NSObject<NSAppearanceCustomization> * */
    fun appearanceSource(): MemorySegment {
        val sel = ObjCRuntime.sel("appearanceSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAppearanceSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAppearanceSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property colorSpace
    fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColorSpace(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColorSpace:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property occlusionState
    fun occlusionState(): NSWindowOcclusionState {
        val sel = ObjCRuntime.sel("occlusionState")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowOcclusionState
    }
    
    // @property titlebarSeparatorStyle
    fun titlebarSeparatorStyle(): NSTitlebarSeparatorStyle {
        val sel = ObjCRuntime.sel("titlebarSeparatorStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTitlebarSeparatorStyle
    }
    fun setTitlebarSeparatorStyle(value: NSTitlebarSeparatorStyle) {
        val sel = ObjCRuntime.sel("setTitlebarSeparatorStyle:")
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
    
    // @property initialFirstResponder
    fun initialFirstResponder(): MemorySegment {
        val sel = ObjCRuntime.sel("initialFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInitialFirstResponder(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInitialFirstResponder:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyViewSelectionDirection
    fun keyViewSelectionDirection(): NSSelectionDirection {
        val sel = ObjCRuntime.sel("keyViewSelectionDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSelectionDirection
    }
    
    // @property defaultButtonCell
    fun defaultButtonCell(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultButtonCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDefaultButtonCell(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDefaultButtonCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autorecalculatesKeyViewLoop
    fun autorecalculatesKeyViewLoop(): BOOL {
        val sel = ObjCRuntime.sel("autorecalculatesKeyViewLoop")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutorecalculatesKeyViewLoop(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutorecalculatesKeyViewLoop:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property toolbar
    fun toolbar(): MemorySegment {
        val sel = ObjCRuntime.sel("toolbar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setToolbar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setToolbar:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsToolbarButton
    fun showsToolbarButton(): BOOL {
        val sel = ObjCRuntime.sel("showsToolbarButton")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsToolbarButton(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsToolbarButton:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsAutomaticWindowTabbing
    }
    }
    
    // @property userTabbingPreference
    }
    
    // @property tabbingMode
    fun tabbingMode(): NSWindowTabbingMode {
        val sel = ObjCRuntime.sel("tabbingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowTabbingMode
    }
    fun setTabbingMode(value: NSWindowTabbingMode) {
        val sel = ObjCRuntime.sel("setTabbingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabbingIdentifier
    fun tabbingIdentifier(): NSWindowTabbingIdentifier {
        val sel = ObjCRuntime.sel("tabbingIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowTabbingIdentifier
    }
    fun setTabbingIdentifier(value: NSWindowTabbingIdentifier) {
        val sel = ObjCRuntime.sel("setTabbingIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabbedWindows
    /** @return NSArray<NSWindow *> * */
    fun tabbedWindows(): MemorySegment {
        val sel = ObjCRuntime.sel("tabbedWindows")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property tab
    fun tab(): MemorySegment {
        val sel = ObjCRuntime.sel("tab")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property tabGroup
    fun tabGroup(): MemorySegment {
        val sel = ObjCRuntime.sel("tabGroup")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hasActiveWindowSharingSession
    fun hasActiveWindowSharingSession(): BOOL {
        val sel = ObjCRuntime.sel("hasActiveWindowSharingSession")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property windowTitlebarLayoutDirection
    fun windowTitlebarLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("windowTitlebarLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    
}

// ── Category: NSEvent on NSWindow ─────────────────────────────────────────

fun NSWindow.trackEventsMatchingMask_timeout_mode_handler(mask: NSEventMask, timeout: NSTimeInterval, mode: NSRunLoopMode, trackingHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("trackEventsMatchingMask:timeout:mode:handler:")
    ObjCRuntime.msgSend(null, ptr, sel, mask, timeout, mode, trackingHandler)
}

fun NSWindow.nextEventMatchingMask(mask: NSEventMask): MemorySegment {
    val sel = ObjCRuntime.sel("nextEventMatchingMask:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask) as MemorySegment
}

fun NSWindow.nextEventMatchingMask_untilDate_inMode_dequeue(mask: NSEventMask, expiration: MemorySegment, mode: NSRunLoopMode, deqFlag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("nextEventMatchingMask:untilDate:inMode:dequeue:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask, expiration, mode, deqFlag) as MemorySegment
}

fun NSWindow.discardEventsMatchingMask_beforeEvent(mask: NSEventMask, lastEvent: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("discardEventsMatchingMask:beforeEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, mask, lastEvent)
}

fun NSWindow.postEvent_atStart(event: MemorySegment, flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("postEvent:atStart:")
    ObjCRuntime.msgSend(null, ptr, sel, event, flag)
}

fun NSWindow.sendEvent(event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sendEvent:")
    ObjCRuntime.msgSend(null, ptr, sel, event)
}

fun NSWindow.currentEvent(): MemorySegment {
    val sel = ObjCRuntime.sel("currentEvent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWindow.acceptsMouseMovedEvents(): BOOL {
    val sel = ObjCRuntime.sel("acceptsMouseMovedEvents")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setAcceptsMouseMovedEvents(acceptsMouseMovedEvents: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAcceptsMouseMovedEvents:")
    ObjCRuntime.msgSend(null, ptr, sel, acceptsMouseMovedEvents)
}

fun NSWindow.ignoresMouseEvents(): BOOL {
    val sel = ObjCRuntime.sel("ignoresMouseEvents")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setIgnoresMouseEvents(ignoresMouseEvents: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIgnoresMouseEvents:")
    ObjCRuntime.msgSend(null, ptr, sel, ignoresMouseEvents)
}

fun NSWindow.mouseLocationOutsideOfEventStream(): NSPoint {
    val sel = ObjCRuntime.sel("mouseLocationOutsideOfEventStream")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
}

// @property currentEvent
    val sel = ObjCRuntime.sel("currentEvent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property acceptsMouseMovedEvents
    val sel = ObjCRuntime.sel("acceptsMouseMovedEvents")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setAcceptsMouseMovedEvents:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property ignoresMouseEvents
    val sel = ObjCRuntime.sel("ignoresMouseEvents")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setIgnoresMouseEvents:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property mouseLocationOutsideOfEventStream
    val sel = ObjCRuntime.sel("mouseLocationOutsideOfEventStream")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
}

// ── Category: NSCursorRect on NSWindow ─────────────────────────────────────────

fun NSWindow.disableCursorRects(): Unit {
    val sel = ObjCRuntime.sel("disableCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.enableCursorRects(): Unit {
    val sel = ObjCRuntime.sel("enableCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.discardCursorRects(): Unit {
    val sel = ObjCRuntime.sel("discardCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.invalidateCursorRectsForView(view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("invalidateCursorRectsForView:")
    ObjCRuntime.msgSend(null, ptr, sel, view)
}

fun NSWindow.resetCursorRects(): Unit {
    val sel = ObjCRuntime.sel("resetCursorRects")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.areCursorRectsEnabled(): BOOL {
    val sel = ObjCRuntime.sel("areCursorRectsEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property areCursorRectsEnabled
    val sel = ObjCRuntime.sel("areCursorRectsEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSDrag on NSWindow ─────────────────────────────────────────

fun NSWindow.beginDraggingSessionWithItems_event_source(items: MemorySegment, event: MemorySegment, source: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("beginDraggingSessionWithItems:event:source:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, items, event, source) as MemorySegment
}

fun NSWindow.dragImage_at_offset_event_pasteboard_source_slideBack(image: MemorySegment, baseLocation: NSPoint, initialOffset: NSSize, event: MemorySegment, pboard: MemorySegment, sourceObj: MemorySegment, slideFlag: BOOL): Unit {
    val sel = ObjCRuntime.sel("dragImage:at:offset:event:pasteboard:source:slideBack:")
    ObjCRuntime.msgSend(null, ptr, sel, image, baseLocation, initialOffset, event, pboard, sourceObj, slideFlag)
}

fun NSWindow.registerForDraggedTypes(newTypes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerForDraggedTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, newTypes)
}

fun NSWindow.unregisterDraggedTypes(): Unit {
    val sel = ObjCRuntime.sel("unregisterDraggedTypes")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSDisplayLink on NSWindow ─────────────────────────────────────────

fun NSWindow.displayLinkWithTarget_selector(target: MemorySegment, selector: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("displayLinkWithTarget:selector:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target, selector) as MemorySegment
}

// ── Category: NSDeprecated on NSWindow ─────────────────────────────────────────

fun NSWindow.cacheImageInRect(rect: NSRect): Unit {
    val sel = ObjCRuntime.sel("cacheImageInRect:")
    ObjCRuntime.msgSend(null, ptr, sel, rect)
}

fun NSWindow.restoreCachedImage(): Unit {
    val sel = ObjCRuntime.sel("restoreCachedImage")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.discardCachedImage(): Unit {
    val sel = ObjCRuntime.sel("discardCachedImage")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.gState(): NSInteger {
    val sel = ObjCRuntime.sel("gState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSWindow.convertBaseToScreen(point: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("convertBaseToScreen:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, point) as NSPoint
}

fun NSWindow.convertScreenToBase(point: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("convertScreenToBase:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, point) as NSPoint
}

fun NSWindow.userSpaceScaleFactor(): CGFloat {
    val sel = ObjCRuntime.sel("userSpaceScaleFactor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
}

fun NSWindow.useOptimizedDrawing(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("useOptimizedDrawing:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSWindow.canStoreColor(): BOOL {
    val sel = ObjCRuntime.sel("canStoreColor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.disableFlushWindow(): Unit {
    val sel = ObjCRuntime.sel("disableFlushWindow")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.enableFlushWindow(): Unit {
    val sel = ObjCRuntime.sel("enableFlushWindow")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.flushWindow(): Unit {
    val sel = ObjCRuntime.sel("flushWindow")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.flushWindowIfNeeded(): Unit {
    val sel = ObjCRuntime.sel("flushWindowIfNeeded")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.initWithWindowRef(windowRef: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithWindowRef:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, windowRef) as MemorySegment
}

fun NSWindow.disableScreenUpdatesUntilFlush(): Unit {
    val sel = ObjCRuntime.sel("disableScreenUpdatesUntilFlush")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.isFlushWindowDisabled(): BOOL {
    val sel = ObjCRuntime.sel("isFlushWindowDisabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isAutodisplay(): BOOL {
    val sel = ObjCRuntime.sel("isAutodisplay")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setAutodisplay(autodisplay: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutodisplay:")
    ObjCRuntime.msgSend(null, ptr, sel, autodisplay)
}

fun NSWindow.graphicsContext(): MemorySegment {
    val sel = ObjCRuntime.sel("graphicsContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWindow.isOneShot(): BOOL {
    val sel = ObjCRuntime.sel("isOneShot")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setOneShot(oneShot: BOOL): Unit {
    val sel = ObjCRuntime.sel("setOneShot:")
    ObjCRuntime.msgSend(null, ptr, sel, oneShot)
}

fun NSWindow.preferredBackingLocation(): NSWindowBackingLocation {
    val sel = ObjCRuntime.sel("preferredBackingLocation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowBackingLocation
}

fun NSWindow.setPreferredBackingLocation(preferredBackingLocation: NSWindowBackingLocation): Unit {
    val sel = ObjCRuntime.sel("setPreferredBackingLocation:")
    ObjCRuntime.msgSend(null, ptr, sel, preferredBackingLocation)
}

fun NSWindow.backingLocation(): NSWindowBackingLocation {
    val sel = ObjCRuntime.sel("backingLocation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowBackingLocation
}

fun NSWindow.showsResizeIndicator(): BOOL {
    val sel = ObjCRuntime.sel("showsResizeIndicator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setShowsResizeIndicator(showsResizeIndicator: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShowsResizeIndicator:")
    ObjCRuntime.msgSend(null, ptr, sel, showsResizeIndicator)
}

fun NSWindow.windowRef(): MemorySegment {
    val sel = ObjCRuntime.sel("windowRef")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSWindow menuChanged:]
fun NSWindow_menuChanged(menu: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("menuChanged:")
    val cls = ObjCRuntime.getClass("NSWindow")
    ObjCRuntime.msgSend(null, cls, sel, menu)
}

// @property flushWindowDisabled
    val sel = ObjCRuntime.sel("isFlushWindowDisabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property autodisplay
    val sel = ObjCRuntime.sel("isAutodisplay")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setAutodisplay:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property graphicsContext
    val sel = ObjCRuntime.sel("graphicsContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property oneShot
    val sel = ObjCRuntime.sel("isOneShot")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setOneShot:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property preferredBackingLocation
    val sel = ObjCRuntime.sel("preferredBackingLocation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowBackingLocation
}
    val sel = ObjCRuntime.sel("setPreferredBackingLocation:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property backingLocation
    val sel = ObjCRuntime.sel("backingLocation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowBackingLocation
}

// @property showsResizeIndicator
    val sel = ObjCRuntime.sel("showsResizeIndicator")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setShowsResizeIndicator:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property windowRef
    val sel = ObjCRuntime.sel("windowRef")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSConstraintBasedLayoutCoreMethods on NSWindow ─────────────────────────────────────────

fun NSWindow.updateConstraintsIfNeeded(): Unit {
    val sel = ObjCRuntime.sel("updateConstraintsIfNeeded")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.layoutIfNeeded(): Unit {
    val sel = ObjCRuntime.sel("layoutIfNeeded")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSConstraintBasedLayoutAnchoring on NSWindow ─────────────────────────────────────────

fun NSWindow.anchorAttributeForOrientation(orientation: NSLayoutConstraintOrientation): NSLayoutAttribute {
    val sel = ObjCRuntime.sel("anchorAttributeForOrientation:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, orientation) as NSLayoutAttribute
}

fun NSWindow.setAnchorAttribute_forOrientation(attr: NSLayoutAttribute, orientation: NSLayoutConstraintOrientation): Unit {
    val sel = ObjCRuntime.sel("setAnchorAttribute:forOrientation:")
    ObjCRuntime.msgSend(null, ptr, sel, attr, orientation)
}

// ── Category: NSConstraintBasedLayoutDebugging on NSWindow ─────────────────────────────────────────

fun NSWindow.visualizeConstraints(constraints: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("visualizeConstraints:")
    ObjCRuntime.msgSend(null, ptr, sel, constraints)
}

// ── Category: NSDrawers on NSWindow ─────────────────────────────────────────

/** @return NSArray<NSDrawer *> * */
fun NSWindow.drawers(): MemorySegment {
    val sel = ObjCRuntime.sel("drawers")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property drawers
/** @return NSArray<NSDrawer *> * */
    val sel = ObjCRuntime.sel("drawers")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSScripting on NSWindow ─────────────────────────────────────────

fun NSWindow.setIsMiniaturized(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIsMiniaturized:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSWindow.setIsVisible(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIsVisible:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSWindow.setIsZoomed(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIsZoomed:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSWindow.handleCloseScriptCommand(command: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("handleCloseScriptCommand:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, command) as MemorySegment
}

fun NSWindow.handlePrintScriptCommand(command: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("handlePrintScriptCommand:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, command) as MemorySegment
}

fun NSWindow.handleSaveScriptCommand(command: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("handleSaveScriptCommand:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, command) as MemorySegment
}

fun NSWindow.hasCloseBox(): BOOL {
    val sel = ObjCRuntime.sel("hasCloseBox")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.hasTitleBar(): BOOL {
    val sel = ObjCRuntime.sel("hasTitleBar")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isFloatingPanel(): BOOL {
    val sel = ObjCRuntime.sel("isFloatingPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isMiniaturizable(): BOOL {
    val sel = ObjCRuntime.sel("isMiniaturizable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isModalPanel(): BOOL {
    val sel = ObjCRuntime.sel("isModalPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isResizable(): BOOL {
    val sel = ObjCRuntime.sel("isResizable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.isZoomable(): BOOL {
    val sel = ObjCRuntime.sel("isZoomable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.orderedIndex(): NSInteger {
    val sel = ObjCRuntime.sel("orderedIndex")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSWindow.setOrderedIndex(orderedIndex: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setOrderedIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, orderedIndex)
}

// @property hasCloseBox
    val sel = ObjCRuntime.sel("hasCloseBox")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property hasTitleBar
    val sel = ObjCRuntime.sel("hasTitleBar")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property floatingPanel
    val sel = ObjCRuntime.sel("isFloatingPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property miniaturizable
    val sel = ObjCRuntime.sel("isMiniaturizable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property modalPanel
    val sel = ObjCRuntime.sel("isModalPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property resizable
    val sel = ObjCRuntime.sel("isResizable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property zoomable
    val sel = ObjCRuntime.sel("isZoomable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property orderedIndex
    val sel = ObjCRuntime.sel("orderedIndex")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}
    val sel = ObjCRuntime.sel("setOrderedIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSUserInterfaceRestoration on NSWindow ─────────────────────────────────────────

fun NSWindow.disableSnapshotRestoration(): Unit {
    val sel = ObjCRuntime.sel("disableSnapshotRestoration")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.enableSnapshotRestoration(): Unit {
    val sel = ObjCRuntime.sel("enableSnapshotRestoration")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWindow.isRestorable(): BOOL {
    val sel = ObjCRuntime.sel("isRestorable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWindow.setRestorable(restorable: BOOL): Unit {
    val sel = ObjCRuntime.sel("setRestorable:")
    ObjCRuntime.msgSend(null, ptr, sel, restorable)
}

/** @return Class<NSWindowRestoration> */
fun NSWindow.restorationClass(): MemorySegment {
    val sel = ObjCRuntime.sel("restorationClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWindow.setRestorationClass(restorationClass: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setRestorationClass:")
    ObjCRuntime.msgSend(null, ptr, sel, restorationClass)
}

// @property restorable
    val sel = ObjCRuntime.sel("isRestorable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setRestorable:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property restorationClass
/** @return Class<NSWindowRestoration> */
    val sel = ObjCRuntime.sel("restorationClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setRestorationClass:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

