package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSplitViewController
 * Superclass: NSViewController
 * Protocols: NSSplitViewDelegate, NSUserInterfaceValidations
 */
open class NSSplitViewController(ptr: MemorySegment) : NSViewController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSplitViewController") }
        
    }
    
    fun addSplitViewItem(splitViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSplitViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, splitViewItem)
    }
    
    fun insertSplitViewItem_atIndex(splitViewItem: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertSplitViewItem:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, splitViewItem, index)
    }
    
    fun removeSplitViewItem(splitViewItem: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeSplitViewItem:")
        ObjCRuntime.msgSend(null, ptr, sel, splitViewItem)
    }
    
    fun splitViewItemForViewController(viewController: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("splitViewItemForViewController:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, viewController) as MemorySegment
    }
    
    fun validateUserInterfaceItem(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("validateUserInterfaceItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    override fun `viewDidLoad`(): Unit {
        val sel = ObjCRuntime.sel("viewDidLoad")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun splitView_canCollapseSubview(splitView: MemorySegment, subview: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("splitView:canCollapseSubview:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, splitView, subview) as BOOL
    }
    
    fun splitView_shouldCollapseSubview_forDoubleClickOnDividerAtIndex(splitView: MemorySegment, subview: MemorySegment, dividerIndex: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("splitView:shouldCollapseSubview:forDoubleClickOnDividerAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, splitView, subview, dividerIndex) as BOOL
    }
    
    fun splitView_shouldHideDividerAtIndex(splitView: MemorySegment, dividerIndex: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("splitView:shouldHideDividerAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, splitView, dividerIndex) as BOOL
    }
    
    fun splitView_effectiveRect_forDrawnRect_ofDividerAtIndex(splitView: MemorySegment, proposedEffectiveRect: NSRect, drawnRect: NSRect, dividerIndex: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("splitView:effectiveRect:forDrawnRect:ofDividerAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, splitView, ObjCRuntime.ObjCStructArg(proposedEffectiveRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(drawnRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), dividerIndex) as NSRect
    }
    
    fun splitView_additionalEffectiveRectOfDividerAtIndex(splitView: MemorySegment, dividerIndex: NSInteger): NSRect {
        val sel = ObjCRuntime.sel("splitView:additionalEffectiveRectOfDividerAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, splitView, dividerIndex) as NSRect
    }
    
    // @property splitView
    fun splitView(): MemorySegment {
        val sel = ObjCRuntime.sel("splitView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSplitView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSplitView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property splitViewItems
    /** @return NSArray<__kindof NSSplitViewItem *> * */
    fun splitViewItems(): MemorySegment {
        val sel = ObjCRuntime.sel("splitViewItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSplitViewItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSplitViewItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumThicknessForInlineSidebars
    fun minimumThicknessForInlineSidebars(): CGFloat {
        val sel = ObjCRuntime.sel("minimumThicknessForInlineSidebars")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumThicknessForInlineSidebars(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumThicknessForInlineSidebars:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSSplitViewControllerToggleSidebarAction on NSSplitViewController ─────────────────────────────────────────

fun NSSplitViewController.toggleSidebar(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleSidebar:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSSplitViewController.toggleInspector(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleInspector:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

