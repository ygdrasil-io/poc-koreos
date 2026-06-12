package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorPanel
 * Superclass: NSPanel
 */
open class NSColorPanel(ptr: MemorySegment) : NSPanel(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorPanel") }
        
        fun dragColor_withEvent_fromView(color: MemorySegment, event: MemorySegment, sourceView: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("dragColor:withEvent:fromView:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, color, event, sourceView) as BOOL
        }
        
        fun setPickerMask(mask: NSColorPanelOptions): Unit {
            val sel = ObjCRuntime.sel("setPickerMask:")
            ObjCRuntime.msgSend(null, _class, sel, mask)
        }
        
        fun setPickerMode(mode: NSColorPanelMode): Unit {
            val sel = ObjCRuntime.sel("setPickerMode:")
            ObjCRuntime.msgSend(null, _class, sel, mode)
        }
        
        fun sharedColorPanel(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedColorPanel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun sharedColorPanelExists(): BOOL {
            val sel = ObjCRuntime.sel("sharedColorPanelExists")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun setAction(selector: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, selector)
    }
    
    fun setTarget(target: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, target)
    }
    
    fun attachColorList(colorList: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("attachColorList:")
        ObjCRuntime.msgSend(null, ptr, sel, colorList)
    }
    
    fun detachColorList(colorList: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detachColorList:")
        ObjCRuntime.msgSend(null, ptr, sel, colorList)
    }
    
    // @property sharedColorPanel
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property continuous
    fun isContinuous(): BOOL {
        val sel = ObjCRuntime.sel("isContinuous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setContinuous(value: BOOL) {
        val sel = ObjCRuntime.sel("setContinuous:")
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
    
    // @property mode
    fun mode(): NSColorPanelMode {
        val sel = ObjCRuntime.sel("mode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorPanelMode
    }
    fun setMode(value: NSColorPanelMode) {
        val sel = ObjCRuntime.sel("setMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property alpha
    fun alpha(): CGFloat {
        val sel = ObjCRuntime.sel("alpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property maximumLinearExposure
    fun maximumLinearExposure(): CGFloat {
        val sel = ObjCRuntime.sel("maximumLinearExposure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMaximumLinearExposure(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaximumLinearExposure:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

