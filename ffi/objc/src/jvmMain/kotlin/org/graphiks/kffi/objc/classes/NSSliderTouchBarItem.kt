package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSliderTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSSliderTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSliderTouchBarItem") }
        
    }
    
    // @property view
    /** @return NSView<NSUserInterfaceCompression> * */
    override fun `view`(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property slider
    fun slider(): MemorySegment {
        val sel = ObjCRuntime.sel("slider")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSlider(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSlider:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setDoubleValue(value: Double) {
        val sel = ObjCRuntime.sel("setDoubleValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumSliderWidth
    fun minimumSliderWidth(): CGFloat {
        val sel = ObjCRuntime.sel("minimumSliderWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumSliderWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumSliderWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumSliderWidth
    fun maximumSliderWidth(): CGFloat {
        val sel = ObjCRuntime.sel("maximumSliderWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaximumSliderWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumSliderWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property label
    fun label(): MemorySegment {
        val sel = ObjCRuntime.sel("label")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun labelAsString(): String = ObjCRuntime.toJavaString(label())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLabel(value: String) = setLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property minimumValueAccessory
    fun minimumValueAccessory(): MemorySegment {
        val sel = ObjCRuntime.sel("minimumValueAccessory")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMinimumValueAccessory(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMinimumValueAccessory:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumValueAccessory
    fun maximumValueAccessory(): MemorySegment {
        val sel = ObjCRuntime.sel("maximumValueAccessory")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMaximumValueAccessory(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMaximumValueAccessory:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property valueAccessoryWidth
    fun valueAccessoryWidth(): NSSliderAccessoryWidth {
        val sel = ObjCRuntime.sel("valueAccessoryWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSSliderAccessoryWidth
    }
    fun setValueAccessoryWidth(value: NSSliderAccessoryWidth) {
        val sel = ObjCRuntime.sel("setValueAccessoryWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property target
    fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property customizationLabel
    override fun `customizationLabel`(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    override fun `customizationLabelAsString`(): String = ObjCRuntime.toJavaString(customizationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCustomizationLabel(value: String) = setCustomizationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
}

