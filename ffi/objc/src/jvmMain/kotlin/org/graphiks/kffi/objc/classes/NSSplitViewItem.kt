/**
 * Kotlin/JVM wrapper for Objective-C class: NSSplitViewItem
 * Superclass: NSObject
 * Protocols: NSAnimatablePropertyContainer, NSCoding
 */
open class NSSplitViewItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSplitViewItem") }
        
        fun splitViewItemWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("splitViewItemWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        fun sidebarWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sidebarWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        fun contentListWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("contentListWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
        fun inspectorWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("inspectorWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
    }
    
    fun addTopAlignedAccessoryViewController(childViewController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTopAlignedAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController)
    }
    
    fun insertTopAlignedAccessoryViewController_atIndex(childViewController: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertTopAlignedAccessoryViewController:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController, index)
    }
    
    fun removeTopAlignedAccessoryViewControllerAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeTopAlignedAccessoryViewControllerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun addBottomAlignedAccessoryViewController(childViewController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addBottomAlignedAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController)
    }
    
    fun insertBottomAlignedAccessoryViewController_atIndex(childViewController: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertBottomAlignedAccessoryViewController:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, childViewController, index)
    }
    
    fun removeBottomAlignedAccessoryViewControllerAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeBottomAlignedAccessoryViewControllerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    // @property behavior
    fun behavior(): NSSplitViewItemBehavior {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewItemBehavior
    }
    
    // @property viewController
    fun viewController(): MemorySegment {
        val sel = ObjCRuntime.sel("viewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsed
    fun isCollapsed(): BOOL {
        val sel = ObjCRuntime.sel("isCollapsed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCollapsed(value: BOOL) {
        val sel = ObjCRuntime.sel("setCollapsed:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canCollapse
    fun canCollapse(): BOOL {
        val sel = ObjCRuntime.sel("canCollapse")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanCollapse(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanCollapse:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapseBehavior
    fun collapseBehavior(): NSSplitViewItemCollapseBehavior {
        val sel = ObjCRuntime.sel("collapseBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewItemCollapseBehavior
    }
    fun setCollapseBehavior(value: NSSplitViewItemCollapseBehavior) {
        val sel = ObjCRuntime.sel("setCollapseBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumThickness
    fun minimumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("minimumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumThickness
    fun maximumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("maximumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaximumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredThicknessFraction
    fun preferredThicknessFraction(): CGFloat {
        val sel = ObjCRuntime.sel("preferredThicknessFraction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setPreferredThicknessFraction(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPreferredThicknessFraction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property holdingPriority
    fun holdingPriority(): NSLayoutPriority {
        val sel = ObjCRuntime.sel("holdingPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSLayoutPriority
    }
    fun setHoldingPriority(value: NSLayoutPriority) {
        val sel = ObjCRuntime.sel("setHoldingPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticMaximumThickness
    fun automaticMaximumThickness(): CGFloat {
        val sel = ObjCRuntime.sel("automaticMaximumThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setAutomaticMaximumThickness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAutomaticMaximumThickness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property springLoaded
    fun isSpringLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isSpringLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSpringLoaded(value: BOOL) {
        val sel = ObjCRuntime.sel("setSpringLoaded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canCollapseFromWindowResize
    fun canCollapseFromWindowResize(): BOOL {
        val sel = ObjCRuntime.sel("canCollapseFromWindowResize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanCollapseFromWindowResize(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanCollapseFromWindowResize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsFullHeightLayout
    fun allowsFullHeightLayout(): BOOL {
        val sel = ObjCRuntime.sel("allowsFullHeightLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsFullHeightLayout(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsFullHeightLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property automaticallyAdjustsSafeAreaInsets
    fun automaticallyAdjustsSafeAreaInsets(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyAdjustsSafeAreaInsets")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyAdjustsSafeAreaInsets(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyAdjustsSafeAreaInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property topAlignedAccessoryViewControllers
    /** @return NSArray<NSSplitViewItemAccessoryViewController *> * */
    fun topAlignedAccessoryViewControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("topAlignedAccessoryViewControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTopAlignedAccessoryViewControllers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTopAlignedAccessoryViewControllers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bottomAlignedAccessoryViewControllers
    /** @return NSArray<NSSplitViewItemAccessoryViewController *> * */
    fun bottomAlignedAccessoryViewControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("bottomAlignedAccessoryViewControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBottomAlignedAccessoryViewControllers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBottomAlignedAccessoryViewControllers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

