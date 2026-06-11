/**
 * Kotlin/JVM wrapper for Objective-C class: NSStackView
 * Superclass: NSView
 */
open class NSStackView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStackView") }
        
        fun stackViewWithViews(views: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("stackViewWithViews:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, views) as MemorySegment
        }
        
    }
    
    fun setCustomSpacing_afterView(spacing: CGFloat, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCustomSpacing:afterView:")
        ObjCRuntime.msgSend(null, ptr, sel, spacing, view)
    }
    
    fun customSpacingAfterView(view: MemorySegment): CGFloat {
        val sel = ObjCRuntime.sel("customSpacingAfterView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, view) as CGFloat
    }
    
    fun addArrangedSubview(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addArrangedSubview:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun insertArrangedSubview_atIndex(view: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertArrangedSubview:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, view, index)
    }
    
    fun removeArrangedSubview(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeArrangedSubview:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun setVisibilityPriority_forView(priority: NSStackViewVisibilityPriority, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setVisibilityPriority:forView:")
        ObjCRuntime.msgSend(null, ptr, sel, priority, view)
    }
    
    fun visibilityPriorityForView(view: MemorySegment): NSStackViewVisibilityPriority {
        val sel = ObjCRuntime.sel("visibilityPriorityForView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, view) as NSStackViewVisibilityPriority
    }
    
    fun clippingResistancePriorityForOrientation(orientation: NSLayoutConstraintOrientation): NSLayoutPriority {
        val sel = ObjCRuntime.sel("clippingResistancePriorityForOrientation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, orientation) as NSLayoutPriority
    }
    
    fun setClippingResistancePriority_forOrientation(clippingResistancePriority: NSLayoutPriority, orientation: NSLayoutConstraintOrientation): Unit {
        val sel = ObjCRuntime.sel("setClippingResistancePriority:forOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, clippingResistancePriority, orientation)
    }
    
    fun huggingPriorityForOrientation(orientation: NSLayoutConstraintOrientation): NSLayoutPriority {
        val sel = ObjCRuntime.sel("huggingPriorityForOrientation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, orientation) as NSLayoutPriority
    }
    
    fun setHuggingPriority_forOrientation(huggingPriority: NSLayoutPriority, orientation: NSLayoutConstraintOrientation): Unit {
        val sel = ObjCRuntime.sel("setHuggingPriority:forOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, huggingPriority, orientation)
    }
    
    // @property delegate
    /** @return id<NSStackViewDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property orientation
    fun orientation(): NSUserInterfaceLayoutOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutOrientation
    }
    fun setOrientation(value: NSUserInterfaceLayoutOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alignment
    fun alignment(): NSLayoutAttribute {
        val sel = ObjCRuntime.sel("alignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLayoutAttribute
    }
    fun setAlignment(value: NSLayoutAttribute) {
        val sel = ObjCRuntime.sel("setAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property edgeInsets
    fun edgeInsets(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("edgeInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setEdgeInsets(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setEdgeInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
    // @property distribution
    fun distribution(): NSStackViewDistribution {
        val sel = ObjCRuntime.sel("distribution")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSStackViewDistribution
    }
    fun setDistribution(value: NSStackViewDistribution) {
        val sel = ObjCRuntime.sel("setDistribution:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property spacing
    fun spacing(): CGFloat {
        val sel = ObjCRuntime.sel("spacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property detachesHiddenViews
    fun detachesHiddenViews(): BOOL {
        val sel = ObjCRuntime.sel("detachesHiddenViews")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDetachesHiddenViews(value: BOOL) {
        val sel = ObjCRuntime.sel("setDetachesHiddenViews:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arrangedSubviews
    /** @return NSArray<__kindof NSView *> * */
    fun arrangedSubviews(): MemorySegment {
        val sel = ObjCRuntime.sel("arrangedSubviews")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property detachedViews
    /** @return NSArray<__kindof NSView *> * */
    fun detachedViews(): MemorySegment {
        val sel = ObjCRuntime.sel("detachedViews")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSStackViewGravityAreas on NSStackView ─────────────────────────────────────────

fun NSStackView.addView_inGravity(view: MemorySegment, gravity: NSStackViewGravity): Unit {
    val sel = ObjCRuntime.sel("addView:inGravity:")
    ObjCRuntime.msgSend(null, ptr, sel, view, gravity)
}

fun NSStackView.insertView_atIndex_inGravity(view: MemorySegment, index: NSUInteger, gravity: NSStackViewGravity): Unit {
    val sel = ObjCRuntime.sel("insertView:atIndex:inGravity:")
    ObjCRuntime.msgSend(null, ptr, sel, view, index, gravity)
}

fun NSStackView.removeView(view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeView:")
    ObjCRuntime.msgSend(null, ptr, sel, view)
}

/** @return NSArray<__kindof NSView *> * */
fun NSStackView.viewsInGravity(gravity: NSStackViewGravity): MemorySegment {
    val sel = ObjCRuntime.sel("viewsInGravity:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, gravity) as MemorySegment
}

fun NSStackView.setViews_inGravity(views: MemorySegment, gravity: NSStackViewGravity): Unit {
    val sel = ObjCRuntime.sel("setViews:inGravity:")
    ObjCRuntime.msgSend(null, ptr, sel, views, gravity)
}

/** @return NSArray<__kindof NSView *> * */
fun NSStackView.views(): MemorySegment {
    val sel = ObjCRuntime.sel("views")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property views
/** @return NSArray<__kindof NSView *> * */
fun NSStackView.views(): MemorySegment {
    val sel = ObjCRuntime.sel("views")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSStackViewDeprecated on NSStackView ─────────────────────────────────────────

fun NSStackView.hasEqualSpacing(): BOOL {
    val sel = ObjCRuntime.sel("hasEqualSpacing")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSStackView.setHasEqualSpacing(hasEqualSpacing: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHasEqualSpacing:")
    ObjCRuntime.msgSend(null, ptr, sel, hasEqualSpacing)
}

// @property hasEqualSpacing
fun NSStackView.hasEqualSpacing(): BOOL {
    val sel = ObjCRuntime.sel("hasEqualSpacing")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSStackView.setHasEqualSpacing(value: BOOL) {
    val sel = ObjCRuntime.sel("setHasEqualSpacing:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

