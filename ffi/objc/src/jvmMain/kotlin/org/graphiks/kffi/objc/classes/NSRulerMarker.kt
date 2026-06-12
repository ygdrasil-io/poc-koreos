package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRulerMarker
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSRulerMarker(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRulerMarker") }
        
    }
    
    open fun initWithRulerView_markerLocation_image_imageOrigin(ruler: MemorySegment, location: CGFloat, image: MemorySegment, imageOrigin: NSPoint): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRulerView:markerLocation:image:imageOrigin:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ruler, location, image, ObjCRuntime.ObjCStructArg(imageOrigin, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun drawRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun trackMouse_adding(mouseDownEvent: MemorySegment, isAdding: BOOL): BOOL {
        val sel = ObjCRuntime.sel("trackMouse:adding:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, mouseDownEvent, isAdding) as BOOL
    }
    
    // @property ruler
    open fun ruler(): MemorySegment {
        val sel = ObjCRuntime.sel("ruler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property markerLocation
    open fun markerLocation(): CGFloat {
        val sel = ObjCRuntime.sel("markerLocation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMarkerLocation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMarkerLocation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property image
    open fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageOrigin
    open fun imageOrigin(): NSPoint {
        val sel = ObjCRuntime.sel("imageOrigin")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    open fun setImageOrigin(value: NSPoint) {
        val sel = ObjCRuntime.sel("setImageOrigin:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    // @property movable
    open fun isMovable(): BOOL {
        val sel = ObjCRuntime.sel("isMovable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setMovable(value: BOOL) {
        val sel = ObjCRuntime.sel("setMovable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property removable
    open fun isRemovable(): BOOL {
        val sel = ObjCRuntime.sel("isRemovable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setRemovable(value: BOOL) {
        val sel = ObjCRuntime.sel("setRemovable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dragging
    open fun isDragging(): BOOL {
        val sel = ObjCRuntime.sel("isDragging")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property representedObject
    /** @return id<NSCopying> */
    open fun representedObject(): MemorySegment {
        val sel = ObjCRuntime.sel("representedObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setRepresentedObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRepresentedObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageRectInRuler
    open fun imageRectInRuler(): NSRect {
        val sel = ObjCRuntime.sel("imageRectInRuler")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property thicknessRequiredInRuler
    open fun thicknessRequiredInRuler(): CGFloat {
        val sel = ObjCRuntime.sel("thicknessRequiredInRuler")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

