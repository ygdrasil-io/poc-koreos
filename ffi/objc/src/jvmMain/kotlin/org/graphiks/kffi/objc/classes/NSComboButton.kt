package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSComboButton
 * Superclass: NSControl
 */
open class NSComboButton(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSComboButton") }
        
        fun comboButtonWithTitle_menu_target_action(title: MemorySegment, menu: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("comboButtonWithTitle:menu:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, menu, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun comboButtonWithTitle_menu_target_action(title: String, menu: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment = comboButtonWithTitle_menu_target_action(ObjCRuntime.newNSString(Arena.global(), title), menu, target, action)
        
        fun comboButtonWithImage_menu_target_action(image: MemorySegment, menu: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("comboButtonWithImage:menu:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, image, menu, target, action) as MemorySegment
        }
        
        fun comboButtonWithTitle_image_menu_target_action(title: MemorySegment, image: MemorySegment, menu: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("comboButtonWithTitle:image:menu:target:action:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title, image, menu, target, action) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun comboButtonWithTitle_image_menu_target_action(title: String, image: MemorySegment, menu: MemorySegment, target: MemorySegment, action: MemorySegment): MemorySegment = comboButtonWithTitle_image_menu_target_action(ObjCRuntime.newNSString(Arena.global(), title), image, menu, target, action)
        
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageScaling
    fun imageScaling(): NSImageScaling {
        val sel = ObjCRuntime.sel("imageScaling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageScaling
    }
    fun setImageScaling(value: NSImageScaling) {
        val sel = ObjCRuntime.sel("setImageScaling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property style
    fun style(): NSComboButtonStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSComboButtonStyle
    }
    fun setStyle(value: NSComboButtonStyle) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

