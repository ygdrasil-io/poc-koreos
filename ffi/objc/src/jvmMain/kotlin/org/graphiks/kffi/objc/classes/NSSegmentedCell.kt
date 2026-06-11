/**
 * Kotlin/JVM wrapper for Objective-C class: NSSegmentedCell
 * Superclass: NSActionCell
 */
open class NSSegmentedCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSegmentedCell") }
        
    }
    
    fun selectSegmentWithTag(tag: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("selectSegmentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tag) as BOOL
    }
    
    fun makeNextSegmentKey(): Unit {
        val sel = ObjCRuntime.sel("makeNextSegmentKey")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun makePreviousSegmentKey(): Unit {
        val sel = ObjCRuntime.sel("makePreviousSegmentKey")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setWidth_forSegment(width: CGFloat, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setWidth:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, width, segment)
    }
    
    fun widthForSegment(segment: NSInteger): CGFloat {
        val sel = ObjCRuntime.sel("widthForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, segment) as CGFloat
    }
    
    fun setImage_forSegment(image: MemorySegment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setImage:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, image, segment)
    }
    
    fun imageForSegment(segment: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("imageForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as MemorySegment
    }
    
    fun setImageScaling_forSegment(scaling: NSImageScaling, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setImageScaling:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, scaling, segment)
    }
    
    fun imageScalingForSegment(segment: NSInteger): NSImageScaling {
        val sel = ObjCRuntime.sel("imageScalingForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as NSImageScaling
    }
    
    fun setLabel_forSegment(label: MemorySegment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setLabel:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, label, segment)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setLabel_forSegment(label: String, segment: NSInteger): Unit = setLabel_forSegment(ObjCRuntime.newNSString(Arena.global(), label), segment)
    
    fun labelForSegment(segment: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("labelForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun labelForSegmentAsString(segment: NSInteger): String = ObjCRuntime.toJavaString(labelForSegment(segment))
    
    fun setSelected_forSegment(selected: BOOL, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setSelected:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, selected, segment)
    }
    
    fun isSelectedForSegment(segment: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isSelectedForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, segment) as BOOL
    }
    
    fun setEnabled_forSegment(enabled: BOOL, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setEnabled:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, enabled, segment)
    }
    
    fun isEnabledForSegment(segment: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isEnabledForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, segment) as BOOL
    }
    
    fun setMenu_forSegment(menu: MemorySegment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setMenu:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, menu, segment)
    }
    
    fun menuForSegment(segment: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("menuForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as MemorySegment
    }
    
    fun setToolTip_forSegment(toolTip: MemorySegment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setToolTip:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, toolTip, segment)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setToolTip_forSegment(toolTip: String, segment: NSInteger): Unit = setToolTip_forSegment(ObjCRuntime.newNSString(Arena.global(), toolTip), segment)
    
    fun toolTipForSegment(segment: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("toolTipForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun toolTipForSegmentAsString(segment: NSInteger): String = ObjCRuntime.toJavaString(toolTipForSegment(segment))
    
    fun setTag_forSegment(tag: NSInteger, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setTag:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, tag, segment)
    }
    
    fun tagForSegment(segment: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("tagForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, segment) as NSInteger
    }
    
    fun drawSegment_inFrame_withView(segment: NSInteger, frame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawSegment:inFrame:withView:")
        ObjCRuntime.msgSend(null, ptr, sel, segment, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    // @property segmentCount
    fun segmentCount(): NSInteger {
        val sel = ObjCRuntime.sel("segmentCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSegmentCount(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSegmentCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedSegment
    fun selectedSegment(): NSInteger {
        val sel = ObjCRuntime.sel("selectedSegment")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedSegment(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property trackingMode
    fun trackingMode(): NSSegmentSwitchTracking {
        val sel = ObjCRuntime.sel("trackingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSegmentSwitchTracking
    }
    fun setTrackingMode(value: NSSegmentSwitchTracking) {
        val sel = ObjCRuntime.sel("setTrackingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property segmentStyle
    fun segmentStyle(): NSSegmentStyle {
        val sel = ObjCRuntime.sel("segmentStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSegmentStyle
    }
    fun setSegmentStyle(value: NSSegmentStyle) {
        val sel = ObjCRuntime.sel("setSegmentStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSSegmentBackgroundStyle on NSSegmentedCell ─────────────────────────────────────────

fun NSSegmentedCell.interiorBackgroundStyleForSegment(segment: NSInteger): NSBackgroundStyle {
    val sel = ObjCRuntime.sel("interiorBackgroundStyleForSegment:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as NSBackgroundStyle
}

