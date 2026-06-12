package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSAffineTransform
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSAffineTransform(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAffineTransform") }
        
        open fun transform(): MemorySegment {
            val sel = ObjCRuntime.sel("transform")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithTransform(transform: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTransform:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, transform) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun translateXBy_yBy(deltaX: CGFloat, deltaY: CGFloat): Unit {
        val sel = ObjCRuntime.sel("translateXBy:yBy:")
        ObjCRuntime.msgSend(null, ptr, sel, deltaX, deltaY)
    }
    
    open fun rotateByDegrees(angle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("rotateByDegrees:")
        ObjCRuntime.msgSend(null, ptr, sel, angle)
    }
    
    open fun rotateByRadians(angle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("rotateByRadians:")
        ObjCRuntime.msgSend(null, ptr, sel, angle)
    }
    
    open fun scaleBy(scale: CGFloat): Unit {
        val sel = ObjCRuntime.sel("scaleBy:")
        ObjCRuntime.msgSend(null, ptr, sel, scale)
    }
    
    open fun scaleXBy_yBy(scaleX: CGFloat, scaleY: CGFloat): Unit {
        val sel = ObjCRuntime.sel("scaleXBy:yBy:")
        ObjCRuntime.msgSend(null, ptr, sel, scaleX, scaleY)
    }
    
    open fun invert(): Unit {
        val sel = ObjCRuntime.sel("invert")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun appendTransform(transform: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, transform)
    }
    
    open fun prependTransform(transform: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("prependTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, transform)
    }
    
    open fun transformPoint(aPoint: NSPoint): NSPoint {
        val sel = ObjCRuntime.sel("transformPoint:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(aPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSPoint
    }
    
    open fun transformSize(aSize: NSSize): NSSize {
        val sel = ObjCRuntime.sel("transformSize:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(aSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    // @property transformStruct
    open fun transformStruct(): NSAffineTransformStruct {
        val sel = ObjCRuntime.sel("transformStruct")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("tX"), ValueLayout.JAVA_DOUBLE.withName("tY")).withName("NSAffineTransformStruct"), ptr, sel) as NSAffineTransformStruct
    }
    open fun setTransformStruct(value: NSAffineTransformStruct) {
        val sel = ObjCRuntime.sel("setTransformStruct:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("tX"), ValueLayout.JAVA_DOUBLE.withName("tY")).withName("NSAffineTransformStruct")))
    }
    
}

// ── Category: NSAppKitAdditions on NSAffineTransform ─────────────────────────────────────────

fun NSAffineTransform.transformBezierPath(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("transformBezierPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
}

fun NSAffineTransform.`set`(): Unit {
    val sel = ObjCRuntime.sel("set")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSAffineTransform.concat(): Unit {
    val sel = ObjCRuntime.sel("concat")
    ObjCRuntime.msgSend(null, ptr, sel)
}

