package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSplitViewItem
 * Superclass: NSObject
 * Protocols: NSAnimatablePropertyContainer, NSCoding
 */
open class NSSplitViewItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSplitViewItem") }
        
        open fun splitViewItemWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("splitViewItemWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        open fun sidebarWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sidebarWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        open fun contentListWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("contentListWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        open fun inspectorWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("inspectorWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
    }
    
    open fun addTopAlignedAccessoryViewController(childViewController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTopAlignedAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController)
    }
    
    open fun insertTopAlignedAccessoryViewController_atIndex(childViewController: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertTopAlignedAccessoryViewController:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController, index)
    }
    
    open fun removeTopAlignedAccessoryViewControllerAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeTopAlignedAccessoryViewControllerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    open fun addBottomAlignedAccessoryViewController(childViewController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addBottomAlignedAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController)
    }
    
    open fun insertBottomAlignedAccessoryViewController_atIndex(childViewController: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertBottomAlignedAccessoryViewController:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController, index)
    }
    
    open fun removeBottomAlignedAccessoryViewControllerAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeBottomAlignedAccessoryViewControllerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    // @property behavior
    open fun behavior(): NSSplitViewItemBehavior {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewItemBehavior
    }
    
    // @property viewController
    open fun viewController(): MemorySegment {
        val sel = ObjCRuntime.sel("viewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsed
    open fun isCollapsed(): BOOL {
        val sel = ObjCRuntime.sel("isCollapsed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setCollapsed(value: BOOL) {
        val sel = ObjCRuntime.sel("setCollapsed:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canCollapse
    open fun canCollapse(): BOOL {
        val sel = ObjCRuntime.sel("canCollapse")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setCanCollapse(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanCollapse:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapseBehavior
    open fun collapseBehavior(): NSSplitViewItemCollapseBehavior {
        val sel = ObjCRuntime.sel("collapseBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewItemCollapseBehavior
    }
    open fun setCollapseBehavior(value: NSSplitViewItemCollapseBehavior) {
        val sel = ObjCRuntime.sel("setCollapseBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumThickness
    open fun minimumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("minimumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMinimumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumThickness
    open fun maximumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("maximumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMaximumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredThicknessFraction
    open fun preferredThicknessFraction(): CGFloat {
        val sel = ObjCRuntime.sel("preferredThicknessFraction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setPreferredThicknessFraction(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPreferredThicknessFraction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property holdingPriority
    open fun holdingPriority(): NSLayoutPriority {
        val sel = ObjCRuntime.sel("holdingPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSLayoutPriority
    }
    open fun setHoldingPriority(value: NSLayoutPriority) {
        val sel = ObjCRuntime.sel("setHoldingPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticMaximumThickness
    open fun automaticMaximumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("automaticMaximumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setAutomaticMaximumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAutomaticMaximumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property springLoaded
    open fun isSpringLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isSpringLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSpringLoaded(value: BOOL) {
        val sel = ObjCRuntime.sel("setSpringLoaded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canCollapseFromWindowResize
    open fun canCollapseFromWindowResize(): BOOL {
        val sel = ObjCRuntime.sel("canCollapseFromWindowResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setCanCollapseFromWindowResize(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanCollapseFromWindowResize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsFullHeightLayout
    open fun allowsFullHeightLayout(): BOOL {
        val sel = ObjCRuntime.sel("allowsFullHeightLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsFullHeightLayout(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsFullHeightLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titlebarSeparatorStyle
    open fun titlebarSeparatorStyle(): NSTitlebarSeparatorStyle {
        val sel = ObjCRuntime.sel("titlebarSeparatorStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTitlebarSeparatorStyle
    }
    open fun setTitlebarSeparatorStyle(value: NSTitlebarSeparatorStyle) {
        val sel = ObjCRuntime.sel("setTitlebarSeparatorStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticallyAdjustsSafeAreaInsets
    open fun automaticallyAdjustsSafeAreaInsets(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyAdjustsSafeAreaInsets")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAutomaticallyAdjustsSafeAreaInsets(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyAdjustsSafeAreaInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property topAlignedAccessoryViewControllers
    /** @return NSArray<NSSplitViewItemAccessoryViewController *> * */
    open fun topAlignedAccessoryViewControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("topAlignedAccessoryViewControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTopAlignedAccessoryViewControllers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTopAlignedAccessoryViewControllers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bottomAlignedAccessoryViewControllers
    /** @return NSArray<NSSplitViewItemAccessoryViewController *> * */
    open fun bottomAlignedAccessoryViewControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("bottomAlignedAccessoryViewControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBottomAlignedAccessoryViewControllers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBottomAlignedAccessoryViewControllers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

