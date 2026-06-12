package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGroupTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSGroupTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGroupTouchBarItem") }
        
        fun groupItemWithIdentifier_items(identifier: NSTouchBarItemIdentifier, items: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("groupItemWithIdentifier:items:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, items) as MemorySegment
        }
        
        fun groupItemWithIdentifier_items_allowedCompressionOptions(identifier: NSTouchBarItemIdentifier, items: MemorySegment, allowedCompressionOptions: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("groupItemWithIdentifier:items:allowedCompressionOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, items, allowedCompressionOptions) as MemorySegment
        }
        
        fun alertStyleGroupItemWithIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("alertStyleGroupItemWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
    }
    
    // @property groupTouchBar
    fun groupTouchBar(): MemorySegment {
        val sel = ObjCRuntime.sel("groupTouchBar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGroupTouchBar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGroupTouchBar:")
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
    
    // @property groupUserInterfaceLayoutDirection
    fun groupUserInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("groupUserInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    fun setGroupUserInterfaceLayoutDirection(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setGroupUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prefersEqualWidths
    fun prefersEqualWidths(): BOOL {
        val sel = ObjCRuntime.sel("prefersEqualWidths")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPrefersEqualWidths(value: BOOL) {
        val sel = ObjCRuntime.sel("setPrefersEqualWidths:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredItemWidth
    fun preferredItemWidth(): CGFloat {
        val sel = ObjCRuntime.sel("preferredItemWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setPreferredItemWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPreferredItemWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property effectiveCompressionOptions
    fun effectiveCompressionOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("effectiveCompressionOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property prioritizedCompressionOptions
    /** @return NSArray<NSUserInterfaceCompressionOptions *> * */
    fun prioritizedCompressionOptions(): MemorySegment {
        val sel = ObjCRuntime.sel("prioritizedCompressionOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrioritizedCompressionOptions(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrioritizedCompressionOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

