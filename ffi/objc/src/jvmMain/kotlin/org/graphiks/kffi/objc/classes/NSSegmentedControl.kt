package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSegmentedControl
 * Superclass: NSControl
 * Protocols: NSUserInterfaceCompression
 */
open class NSSegmentedControl(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSegmentedControl") }
        
    }
    
    fun selectSegmentWithTag(tag: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("selectSegmentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tag) as BOOL
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
    
    fun setMenu_forSegment(menu: MemorySegment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setMenu:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, menu, segment)
    }
    
    fun menuForSegment(segment: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("menuForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as MemorySegment
    }
    
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
    
    fun setShowsMenuIndicator_forSegment(showsMenuIndicator: BOOL, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setShowsMenuIndicator:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, showsMenuIndicator, segment)
    }
    
    fun showsMenuIndicatorForSegment(segment: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("showsMenuIndicatorForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, segment) as BOOL
    }
    
    fun setAlignment_forSegment(alignment: NSTextAlignment, segment: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setAlignment:forSegment:")
        ObjCRuntime.msgSend(null, ptr, sel, alignment, segment)
    }
    
    fun alignmentForSegment(segment: NSInteger): NSTextAlignment {
        val sel = ObjCRuntime.sel("alignmentForSegment:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, segment) as NSTextAlignment
    }
    
    fun compressWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("compressWithPrioritizedCompressionOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, prioritizedOptions)
    }
    
    fun minimumSizeWithPrioritizedCompressionOptions(prioritizedOptions: MemorySegment): NSSize {
        val sel = ObjCRuntime.sel("minimumSizeWithPrioritizedCompressionOptions:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, prioritizedOptions) as NSSize
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
    
    // @property segmentStyle
    fun segmentStyle(): NSSegmentStyle {
        val sel = ObjCRuntime.sel("segmentStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSegmentStyle
    }
    fun setSegmentStyle(value: NSSegmentStyle) {
        val sel = ObjCRuntime.sel("setSegmentStyle:")
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
    
    // @property trackingMode
    fun trackingMode(): NSSegmentSwitchTracking {
        val sel = ObjCRuntime.sel("trackingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSegmentSwitchTracking
    }
    fun setTrackingMode(value: NSSegmentSwitchTracking) {
        val sel = ObjCRuntime.sel("setTrackingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleValueForSelectedSegment
    fun doubleValueForSelectedSegment(): Double {
        val sel = ObjCRuntime.sel("doubleValueForSelectedSegment")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    
    // @property selectedSegmentBezelColor
    fun selectedSegmentBezelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedSegmentBezelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectedSegmentBezelColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectedSegmentBezelColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indexOfSelectedItem
    fun indexOfSelectedItem(): NSInteger {
        val sel = ObjCRuntime.sel("indexOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property segmentDistribution
    fun segmentDistribution(): NSSegmentDistribution {
        val sel = ObjCRuntime.sel("segmentDistribution")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSegmentDistribution
    }
    fun setSegmentDistribution(value: NSSegmentDistribution) {
        val sel = ObjCRuntime.sel("setSegmentDistribution:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property activeCompressionOptions
    fun activeCompressionOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("activeCompressionOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property borderShape
    fun borderShape(): NSControlBorderShape {
        val sel = ObjCRuntime.sel("borderShape")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlBorderShape
    }
    fun setBorderShape(value: NSControlBorderShape) {
        val sel = ObjCRuntime.sel("setBorderShape:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSSegmentedControlConvenience on NSSegmentedControl ─────────────────────────────────────────

// Class<*> method: +[NSSegmentedControl segmentedControlWithLabels:trackingMode:target:action:]
fun NSSegmentedControl_segmentedControlWithLabels_trackingMode_target_action(labels: MemorySegment, trackingMode: NSSegmentSwitchTracking, target: MemorySegment, action: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("segmentedControlWithLabels:trackingMode:target:action:")
    val cls = ObjCRuntime.getClass("NSSegmentedControl")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, labels, trackingMode, target, action) as MemorySegment
}

// Class<*> method: +[NSSegmentedControl segmentedControlWithImages:trackingMode:target:action:]
fun NSSegmentedControl_segmentedControlWithImages_trackingMode_target_action(images: MemorySegment, trackingMode: NSSegmentSwitchTracking, target: MemorySegment, action: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("segmentedControlWithImages:trackingMode:target:action:")
    val cls = ObjCRuntime.getClass("NSSegmentedControl")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, images, trackingMode, target, action) as MemorySegment
}

