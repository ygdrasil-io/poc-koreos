package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorPickerTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSColorPickerTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorPickerTouchBarItem") }
        
        fun colorPickerWithIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("colorPickerWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        fun textColorPickerWithIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("textColorPickerWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        fun strokeColorPickerWithIdentifier(identifier: NSTouchBarItemIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("strokeColorPickerWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        fun colorPickerWithIdentifier_buttonImage(identifier: NSTouchBarItemIdentifier, image: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorPickerWithIdentifier:buttonImage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier, image) as MemorySegment
        }
        
    }
    
    // @property color
    fun color(): MemorySegment {
        val sel = ObjCRuntime.sel("color")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsAlpha
    fun showsAlpha(): BOOL {
        val sel = ObjCRuntime.sel("showsAlpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsAlpha(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedColorSpaces
    /** @return NSArray<NSColorSpace *> * */
    fun allowedColorSpaces(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedColorSpaces")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedColorSpaces(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedColorSpaces:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property colorList
    fun colorList(): MemorySegment {
        val sel = ObjCRuntime.sel("colorList")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColorList(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColorList:")
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
    
}

