package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSVisualEffectView
 * Superclass: NSView
 */
open class NSVisualEffectView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSVisualEffectView") }
        
    }
    
    override fun `viewDidMoveToWindow`(): Unit {
        val sel = ObjCRuntime.sel("viewDidMoveToWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    override fun `viewWillMoveToWindow`(newWindow: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewWillMoveToWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, newWindow)
    }
    
    // @property material
    fun material(): NSVisualEffectMaterial {
        val sel = ObjCRuntime.sel("material")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSVisualEffectMaterial
    }
    fun setMaterial(value: NSVisualEffectMaterial) {
        val sel = ObjCRuntime.sel("setMaterial:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interiorBackgroundStyle
    fun interiorBackgroundStyle(): NSBackgroundStyle {
        val sel = ObjCRuntime.sel("interiorBackgroundStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
    }
    
    // @property blendingMode
    fun blendingMode(): NSVisualEffectBlendingMode {
        val sel = ObjCRuntime.sel("blendingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSVisualEffectBlendingMode
    }
    fun setBlendingMode(value: NSVisualEffectBlendingMode) {
        val sel = ObjCRuntime.sel("setBlendingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    fun state(): NSVisualEffectState {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSVisualEffectState
    }
    fun setState(value: NSVisualEffectState) {
        val sel = ObjCRuntime.sel("setState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maskImage
    fun maskImage(): MemorySegment {
        val sel = ObjCRuntime.sel("maskImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMaskImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMaskImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property emphasized
    fun isEmphasized(): BOOL {
        val sel = ObjCRuntime.sel("isEmphasized")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEmphasized(value: BOOL) {
        val sel = ObjCRuntime.sel("setEmphasized:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

