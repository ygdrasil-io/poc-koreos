package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSBezierPath
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSBezierPath(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBezierPath") }
        
        open fun bezierPath(): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPath")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun bezierPathWithRect(rect: NSRect): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithRect:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
        }
        
        open fun bezierPathWithOvalInRect(rect: NSRect): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithOvalInRect:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
        }
        
        open fun bezierPathWithRoundedRect_xRadius_yRadius(rect: NSRect, xRadius: CGFloat, yRadius: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithRoundedRect:xRadius:yRadius:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), xRadius, yRadius) as MemorySegment
        }
        
        open fun bezierPathWithCGPath(cgPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithCGPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cgPath) as MemorySegment
        }
        
        open fun fillRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("fillRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        open fun strokeRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("strokeRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        open fun clipRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("clipRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        open fun strokeLineFromPoint_toPoint(point1: NSPoint, point2: NSPoint): Unit {
            val sel = ObjCRuntime.sel("strokeLineFromPoint:toPoint:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(point1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(point2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
        }
        
        open fun drawPackedGlyphs_atPoint(packedGlyphs: MemorySegment, point: NSPoint): Unit {
            val sel = ObjCRuntime.sel("drawPackedGlyphs:atPoint:")
            ObjCRuntime.msgSend(null, _class, sel, packedGlyphs, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
        }
        
        open fun defaultMiterLimit(): CGFloat {
            val sel = ObjCRuntime.sel("defaultMiterLimit")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        open fun setDefaultMiterLimit(defaultMiterLimit: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultMiterLimit:")
            ObjCRuntime.msgSend(null, _class, sel, defaultMiterLimit)
        }
        
        open fun defaultFlatness(): CGFloat {
            val sel = ObjCRuntime.sel("defaultFlatness")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        open fun setDefaultFlatness(defaultFlatness: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultFlatness:")
            ObjCRuntime.msgSend(null, _class, sel, defaultFlatness)
        }
        
        open fun defaultWindingRule(): NSWindingRule {
            val sel = ObjCRuntime.sel("defaultWindingRule")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSWindingRule
        }
        
        open fun setDefaultWindingRule(defaultWindingRule: NSWindingRule): Unit {
            val sel = ObjCRuntime.sel("setDefaultWindingRule:")
            ObjCRuntime.msgSend(null, _class, sel, defaultWindingRule)
        }
        
        open fun defaultLineCapStyle(): NSLineCapStyle {
            val sel = ObjCRuntime.sel("defaultLineCapStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSLineCapStyle
        }
        
        open fun setDefaultLineCapStyle(defaultLineCapStyle: NSLineCapStyle): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineCapStyle:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineCapStyle)
        }
        
        open fun defaultLineJoinStyle(): NSLineJoinStyle {
            val sel = ObjCRuntime.sel("defaultLineJoinStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSLineJoinStyle
        }
        
        open fun setDefaultLineJoinStyle(defaultLineJoinStyle: NSLineJoinStyle): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineJoinStyle:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineJoinStyle)
        }
        
        open fun defaultLineWidth(): CGFloat {
            val sel = ObjCRuntime.sel("defaultLineWidth")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        open fun setDefaultLineWidth(defaultLineWidth: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineWidth:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineWidth)
        }
        
    }
    
    open fun moveToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("moveToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun lineToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("lineToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun curveToPoint_controlPoint1_controlPoint2(endPoint: NSPoint, controlPoint1: NSPoint, controlPoint2: NSPoint): Unit {
        val sel = ObjCRuntime.sel("curveToPoint:controlPoint1:controlPoint2:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun curveToPoint_controlPoint(endPoint: NSPoint, controlPoint: NSPoint): Unit {
        val sel = ObjCRuntime.sel("curveToPoint:controlPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun closePath(): Unit {
        val sel = ObjCRuntime.sel("closePath")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun removeAllPoints(): Unit {
        val sel = ObjCRuntime.sel("removeAllPoints")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun relativeMoveToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeMoveToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun relativeLineToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeLineToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun relativeCurveToPoint_controlPoint1_controlPoint2(endPoint: NSPoint, controlPoint1: NSPoint, controlPoint2: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeCurveToPoint:controlPoint1:controlPoint2:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun relativeCurveToPoint_controlPoint(endPoint: NSPoint, controlPoint: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeCurveToPoint:controlPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    open fun getLineDash_count_phase(pattern: MemorySegment, count: MemorySegment, phase: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getLineDash:count:phase:")
        ObjCRuntime.msgSend(null, ptr, sel, pattern, count, phase)
    }
    
    open fun setLineDash_count_phase(pattern: MemorySegment, count: NSInteger, phase: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setLineDash:count:phase:")
        ObjCRuntime.msgSend(null, ptr, sel, pattern, count, phase)
    }
    
    open fun stroke(): Unit {
        val sel = ObjCRuntime.sel("stroke")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun fill(): Unit {
        val sel = ObjCRuntime.sel("fill")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun addClip(): Unit {
        val sel = ObjCRuntime.sel("addClip")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun setClip(): Unit {
        val sel = ObjCRuntime.sel("setClip")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun transformUsingAffineTransform(transform: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("transformUsingAffineTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, transform)
    }
    
    open fun elementAtIndex_associatedPoints(index: NSInteger, points: MemorySegment): NSBezierPathElement {
        val sel = ObjCRuntime.sel("elementAtIndex:associatedPoints:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, points) as NSBezierPathElement
    }
    
    open fun elementAtIndex(index: NSInteger): NSBezierPathElement {
        val sel = ObjCRuntime.sel("elementAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as NSBezierPathElement
    }
    
    open fun setAssociatedPoints_atIndex(points: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setAssociatedPoints:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, points, index)
    }
    
    open fun appendBezierPath(path: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPath:")
        ObjCRuntime.msgSend(null, ptr, sel, path)
    }
    
    open fun appendBezierPathWithRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun appendBezierPathWithPoints_count(points: MemorySegment, count: NSInteger): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithPoints:count:")
        ObjCRuntime.msgSend(null, ptr, sel, points, count)
    }
    
    open fun appendBezierPathWithOvalInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithOvalInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun appendBezierPathWithArcWithCenter_radius_startAngle_endAngle_clockwise(center: NSPoint, radius: CGFloat, startAngle: CGFloat, endAngle: CGFloat, clockwise: BOOL): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcWithCenter:radius:startAngle:endAngle:clockwise:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(center, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius, startAngle, endAngle, clockwise)
    }
    
    open fun appendBezierPathWithArcWithCenter_radius_startAngle_endAngle(center: NSPoint, radius: CGFloat, startAngle: CGFloat, endAngle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcWithCenter:radius:startAngle:endAngle:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(center, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius, startAngle, endAngle)
    }
    
    open fun appendBezierPathWithArcFromPoint_toPoint_radius(point1: NSPoint, point2: NSPoint, radius: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcFromPoint:toPoint:radius:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(point2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius)
    }
    
    open fun appendBezierPathWithCGGlyph_inFont(glyph: CGGlyph, font: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithCGGlyph:inFont:")
        ObjCRuntime.msgSend(null, ptr, sel, glyph, font)
    }
    
    open fun appendBezierPathWithCGGlyphs_count_inFont(glyphs: MemorySegment, count: NSInteger, font: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithCGGlyphs:count:inFont:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphs, count, font)
    }
    
    open fun appendBezierPathWithRoundedRect_xRadius_yRadius(rect: NSRect, xRadius: CGFloat, yRadius: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithRoundedRect:xRadius:yRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), xRadius, yRadius)
    }
    
    open fun containsPoint(point: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("containsPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    // @property CGPath
    open fun CGPath(): MemorySegment {
        val sel = ObjCRuntime.sel("CGPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCGPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCGPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultMiterLimit
    open fun lineWidth(): CGFloat {
        val sel = ObjCRuntime.sel("lineWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setLineWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineCapStyle
    open fun lineCapStyle(): NSLineCapStyle {
        val sel = ObjCRuntime.sel("lineCapStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineCapStyle
    }
    open fun setLineCapStyle(value: NSLineCapStyle) {
        val sel = ObjCRuntime.sel("setLineCapStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineJoinStyle
    open fun lineJoinStyle(): NSLineJoinStyle {
        val sel = ObjCRuntime.sel("lineJoinStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineJoinStyle
    }
    open fun setLineJoinStyle(value: NSLineJoinStyle) {
        val sel = ObjCRuntime.sel("setLineJoinStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property windingRule
    open fun windingRule(): NSWindingRule {
        val sel = ObjCRuntime.sel("windingRule")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindingRule
    }
    open fun setWindingRule(value: NSWindingRule) {
        val sel = ObjCRuntime.sel("setWindingRule:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property miterLimit
    open fun miterLimit(): CGFloat {
        val sel = ObjCRuntime.sel("miterLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMiterLimit(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMiterLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property flatness
    open fun flatness(): CGFloat {
        val sel = ObjCRuntime.sel("flatness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setFlatness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFlatness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezierPathByFlatteningPath
    open fun bezierPathByFlatteningPath(): MemorySegment {
        val sel = ObjCRuntime.sel("bezierPathByFlatteningPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bezierPathByReversingPath
    open fun bezierPathByReversingPath(): MemorySegment {
        val sel = ObjCRuntime.sel("bezierPathByReversingPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property empty
    open fun isEmpty(): BOOL {
        val sel = ObjCRuntime.sel("isEmpty")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property currentPoint
    open fun currentPoint(): NSPoint {
        val sel = ObjCRuntime.sel("currentPoint")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property controlPointBounds
    open fun controlPointBounds(): NSRect {
        val sel = ObjCRuntime.sel("controlPointBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property bounds
    open fun bounds(): NSRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property elementCount
    open fun elementCount(): NSInteger {
        val sel = ObjCRuntime.sel("elementCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

// ── Category: NSBezierPathDeprecated on NSBezierPath ─────────────────────────────────────────

fun NSBezierPath.cachesBezierPath(): BOOL {
    val sel = ObjCRuntime.sel("cachesBezierPath")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSBezierPath.setCachesBezierPath(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setCachesBezierPath:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSBezierPath.appendBezierPathWithGlyph_inFont(glyph: NSGlyph, font: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendBezierPathWithGlyph:inFont:")
    ObjCRuntime.msgSend(null, ptr, sel, glyph, font)
}

fun NSBezierPath.appendBezierPathWithGlyphs_count_inFont(glyphs: MemorySegment, count: NSInteger, font: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendBezierPathWithGlyphs:count:inFont:")
    ObjCRuntime.msgSend(null, ptr, sel, glyphs, count, font)
}

fun NSBezierPath.appendBezierPathWithPackedGlyphs(packedGlyphs: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("appendBezierPathWithPackedGlyphs:")
    ObjCRuntime.msgSend(null, ptr, sel, packedGlyphs)
}

