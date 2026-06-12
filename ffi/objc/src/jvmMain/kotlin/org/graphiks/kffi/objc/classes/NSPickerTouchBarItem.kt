package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPickerTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSPickerTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPickerTouchBarItem") }
        
        fun pickerTouchBarItemWithIdentifier_labels_selectionMode_target_action(identifier: NSTouchBarItemIdentifier, labels: MemorySegment, selectionMode: NSPickerTouchBarItemSelectionMode, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("pickerTouchBarItemWithIdentifier:labels:selectionMode:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, labels, selectionMode, target, action) as MemorySegment
        }
        
        fun pickerTouchBarItemWithIdentifier_images_selectionMode_target_action(identifier: NSTouchBarItemIdentifier, images: MemorySegment, selectionMode: NSPickerTouchBarItemSelectionMode, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("pickerTouchBarItemWithIdentifier:images:selectionMode:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, images, selectionMode, target, action) as MemorySegment
        }
        
    }
    
    fun setImage_atIndex(image: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setImage:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, image, index)
    }
    
    fun imageAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("imageAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun setLabel_atIndex(label: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setLabel:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, label, index)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setLabel_atIndex(label: String, index: NSInteger): Unit = setLabel_atIndex(ObjCRuntime.newNSString(Arena.global(), label), index)
    
    fun labelAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("labelAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun labelAtIndexAsString(index: NSInteger): String = ObjCRuntime.toJavaString(labelAtIndex(index))
    
    fun setEnabled_atIndex(enabled: BOOL, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setEnabled:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, enabled, index)
    }
    
    fun isEnabledAtIndex(index: NSInteger): BOOL {
        val sel = ObjCRuntime.sel("isEnabledAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, index) as BOOL
    }
    
    // @property controlRepresentation
    fun controlRepresentation(): NSPickerTouchBarItemControlRepresentation {
        val sel = ObjCRuntime.sel("controlRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPickerTouchBarItemControlRepresentation
    }
    fun setControlRepresentation(value: NSPickerTouchBarItemControlRepresentation) {
        val sel = ObjCRuntime.sel("setControlRepresentation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsedRepresentationLabel
    fun collapsedRepresentationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("collapsedRepresentationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollapsedRepresentationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollapsedRepresentationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun collapsedRepresentationLabelAsString(): String = ObjCRuntime.toJavaString(collapsedRepresentationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCollapsedRepresentationLabel(value: String) = setCollapsedRepresentationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property collapsedRepresentationImage
    fun collapsedRepresentationImage(): MemorySegment {
        val sel = ObjCRuntime.sel("collapsedRepresentationImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollapsedRepresentationImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollapsedRepresentationImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedIndex
    fun selectedIndex(): NSInteger {
        val sel = ObjCRuntime.sel("selectedIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSelectedIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSelectedIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionColor
    fun selectionColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionMode
    fun selectionMode(): NSPickerTouchBarItemSelectionMode {
        val sel = ObjCRuntime.sel("selectionMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPickerTouchBarItemSelectionMode
    }
    fun setSelectionMode(value: NSPickerTouchBarItemSelectionMode) {
        val sel = ObjCRuntime.sel("setSelectionMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfOptions
    fun numberOfOptions(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfOptions")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfOptions(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfOptions:")
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
    
    // @property enabled
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
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

