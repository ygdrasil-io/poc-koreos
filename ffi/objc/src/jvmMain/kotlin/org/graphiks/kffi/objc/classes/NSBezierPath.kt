/**
 * Kotlin/JVM wrapper for Objective-C class: NSBezierPath
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSBezierPath(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBezierPath") }
        
        fun bezierPath(): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPath")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun bezierPathWithRect(rect: NSRect): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithRect:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
        }
        
        fun bezierPathWithOvalInRect(rect: NSRect): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithOvalInRect:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
        }
        
        fun bezierPathWithRoundedRect_xRadius_yRadius(rect: NSRect, xRadius: CGFloat, yRadius: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithRoundedRect:xRadius:yRadius:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), xRadius, yRadius) as MemorySegment
        }
        
        fun bezierPathWithCGPath(cgPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bezierPathWithCGPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cgPath) as MemorySegment
        }
        
        fun fillRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("fillRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        fun strokeRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("strokeRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        fun clipRect(rect: NSRect): Unit {
            val sel = ObjCRuntime.sel("clipRect:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
        }
        
        fun strokeLineFromPoint_toPoint(point1: NSPoint, point2: NSPoint): Unit {
            val sel = ObjCRuntime.sel("strokeLineFromPoint:toPoint:")
            ObjCRuntime.msgSend(null, _class, sel, ObjCRuntime.ObjCStructArg(point1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(point2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
        }
        
        fun drawPackedGlyphs_atPoint(packedGlyphs: MemorySegment, point: NSPoint): Unit {
            val sel = ObjCRuntime.sel("drawPackedGlyphs:atPoint:")
            ObjCRuntime.msgSend(null, _class, sel, packedGlyphs, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
        }
        
        fun defaultMiterLimit(): CGFloat {
            val sel = ObjCRuntime.sel("defaultMiterLimit")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        fun setDefaultMiterLimit(defaultMiterLimit: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultMiterLimit:")
            ObjCRuntime.msgSend(null, _class, sel, defaultMiterLimit)
        }
        
        fun defaultFlatness(): CGFloat {
            val sel = ObjCRuntime.sel("defaultFlatness")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        fun setDefaultFlatness(defaultFlatness: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultFlatness:")
            ObjCRuntime.msgSend(null, _class, sel, defaultFlatness)
        }
        
        fun defaultWindingRule(): NSWindingRule {
            val sel = ObjCRuntime.sel("defaultWindingRule")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSWindingRule
        }
        
        fun setDefaultWindingRule(defaultWindingRule: NSWindingRule): Unit {
            val sel = ObjCRuntime.sel("setDefaultWindingRule:")
            ObjCRuntime.msgSend(null, _class, sel, defaultWindingRule)
        }
        
        fun defaultLineCapStyle(): NSLineCapStyle {
            val sel = ObjCRuntime.sel("defaultLineCapStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSLineCapStyle
        }
        
        fun setDefaultLineCapStyle(defaultLineCapStyle: NSLineCapStyle): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineCapStyle:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineCapStyle)
        }
        
        fun defaultLineJoinStyle(): NSLineJoinStyle {
            val sel = ObjCRuntime.sel("defaultLineJoinStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSLineJoinStyle
        }
        
        fun setDefaultLineJoinStyle(defaultLineJoinStyle: NSLineJoinStyle): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineJoinStyle:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineJoinStyle)
        }
        
        fun defaultLineWidth(): CGFloat {
            val sel = ObjCRuntime.sel("defaultLineWidth")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel) as CGFloat
        }
        
        fun setDefaultLineWidth(defaultLineWidth: CGFloat): Unit {
            val sel = ObjCRuntime.sel("setDefaultLineWidth:")
            ObjCRuntime.msgSend(null, _class, sel, defaultLineWidth)
        }
        
    }
    
    fun moveToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("moveToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun lineToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("lineToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun curveToPoint_controlPoint1_controlPoint2(endPoint: NSPoint, controlPoint1: NSPoint, controlPoint2: NSPoint): Unit {
        val sel = ObjCRuntime.sel("curveToPoint:controlPoint1:controlPoint2:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun curveToPoint_controlPoint(endPoint: NSPoint, controlPoint: NSPoint): Unit {
        val sel = ObjCRuntime.sel("curveToPoint:controlPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun closePath(): Unit {
        val sel = ObjCRuntime.sel("closePath")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun removeAllPoints(): Unit {
        val sel = ObjCRuntime.sel("removeAllPoints")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun relativeMoveToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeMoveToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun relativeLineToPoint(point: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeLineToPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun relativeCurveToPoint_controlPoint1_controlPoint2(endPoint: NSPoint, controlPoint1: NSPoint, controlPoint2: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeCurveToPoint:controlPoint1:controlPoint2:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun relativeCurveToPoint_controlPoint(endPoint: NSPoint, controlPoint: NSPoint): Unit {
        val sel = ObjCRuntime.sel("relativeCurveToPoint:controlPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(endPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(controlPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun getLineDash_count_phase(pattern: MemorySegment, count: MemorySegment, phase: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getLineDash:count:phase:")
        ObjCRuntime.msgSend(null, ptr, sel, pattern, count, phase)
    }
    
    fun setLineDash_count_phase(pattern: MemorySegment, count: NSInteger, phase: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setLineDash:count:phase:")
        ObjCRuntime.msgSend(null, ptr, sel, pattern, count, phase)
    }
    
    fun stroke(): Unit {
        val sel = ObjCRuntime.sel("stroke")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun fill(): Unit {
        val sel = ObjCRuntime.sel("fill")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addClip(): Unit {
        val sel = ObjCRuntime.sel("addClip")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setClip(): Unit {
        val sel = ObjCRuntime.sel("setClip")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun transformUsingAffineTransform(transform: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("transformUsingAffineTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, transform)
    }
    
    fun elementAtIndex_associatedPoints(index: NSInteger, points: MemorySegment): NSBezierPathElement {
        val sel = ObjCRuntime.sel("elementAtIndex:associatedPoints:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, points) as NSBezierPathElement
    }
    
    fun elementAtIndex(index: NSInteger): NSBezierPathElement {
        val sel = ObjCRuntime.sel("elementAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as NSBezierPathElement
    }
    
    fun setAssociatedPoints_atIndex(points: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setAssociatedPoints:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, points, index)
    }
    
    fun appendBezierPath(path: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPath:")
        ObjCRuntime.msgSend(null, ptr, sel, path)
    }
    
    fun appendBezierPathWithRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun appendBezierPathWithPoints_count(points: MemorySegment, count: NSInteger): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithPoints:count:")
        ObjCRuntime.msgSend(null, ptr, sel, points, count)
    }
    
    fun appendBezierPathWithOvalInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithOvalInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun appendBezierPathWithArcWithCenter_radius_startAngle_endAngle_clockwise(center: NSPoint, radius: CGFloat, startAngle: CGFloat, endAngle: CGFloat, clockwise: BOOL): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcWithCenter:radius:startAngle:endAngle:clockwise:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(center, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius, startAngle, endAngle, clockwise)
    }
    
    fun appendBezierPathWithArcWithCenter_radius_startAngle_endAngle(center: NSPoint, radius: CGFloat, startAngle: CGFloat, endAngle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcWithCenter:radius:startAngle:endAngle:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(center, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius, startAngle, endAngle)
    }
    
    fun appendBezierPathWithArcFromPoint_toPoint_radius(point1: NSPoint, point2: NSPoint, radius: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithArcFromPoint:toPoint:radius:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point1, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(point2, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), radius)
    }
    
    fun appendBezierPathWithCGGlyph_inFont(glyph: CGGlyph, font: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithCGGlyph:inFont:")
        ObjCRuntime.msgSend(null, ptr, sel, glyph, font)
    }
    
    fun appendBezierPathWithCGGlyphs_count_inFont(glyphs: MemorySegment, count: NSInteger, font: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithCGGlyphs:count:inFont:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphs, count, font)
    }
    
    fun appendBezierPathWithRoundedRect_xRadius_yRadius(rect: NSRect, xRadius: CGFloat, yRadius: CGFloat): Unit {
        val sel = ObjCRuntime.sel("appendBezierPathWithRoundedRect:xRadius:yRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), xRadius, yRadius)
    }
    
    fun containsPoint(point: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("containsPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    // @property CGPath
    fun CGPath(): MemorySegment {
        val sel = ObjCRuntime.sel("CGPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCGPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCGPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultMiterLimit
    fun defaultMiterLimit(): CGFloat {
        val sel = ObjCRuntime.sel("defaultMiterLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setDefaultMiterLimit(value: CGFloat) {
        val sel = ObjCRuntime.sel("setDefaultMiterLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultFlatness
    fun defaultFlatness(): CGFloat {
        val sel = ObjCRuntime.sel("defaultFlatness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setDefaultFlatness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setDefaultFlatness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultWindingRule
    fun defaultWindingRule(): NSWindingRule {
        val sel = ObjCRuntime.sel("defaultWindingRule")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindingRule
    }
    fun setDefaultWindingRule(value: NSWindingRule) {
        val sel = ObjCRuntime.sel("setDefaultWindingRule:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultLineCapStyle
    fun defaultLineCapStyle(): NSLineCapStyle {
        val sel = ObjCRuntime.sel("defaultLineCapStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineCapStyle
    }
    fun setDefaultLineCapStyle(value: NSLineCapStyle) {
        val sel = ObjCRuntime.sel("setDefaultLineCapStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultLineJoinStyle
    fun defaultLineJoinStyle(): NSLineJoinStyle {
        val sel = ObjCRuntime.sel("defaultLineJoinStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineJoinStyle
    }
    fun setDefaultLineJoinStyle(value: NSLineJoinStyle) {
        val sel = ObjCRuntime.sel("setDefaultLineJoinStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultLineWidth
    fun defaultLineWidth(): CGFloat {
        val sel = ObjCRuntime.sel("defaultLineWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setDefaultLineWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setDefaultLineWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineWidth
    fun lineWidth(): CGFloat {
        val sel = ObjCRuntime.sel("lineWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLineWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineCapStyle
    fun lineCapStyle(): NSLineCapStyle {
        val sel = ObjCRuntime.sel("lineCapStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineCapStyle
    }
    fun setLineCapStyle(value: NSLineCapStyle) {
        val sel = ObjCRuntime.sel("setLineCapStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineJoinStyle
    fun lineJoinStyle(): NSLineJoinStyle {
        val sel = ObjCRuntime.sel("lineJoinStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineJoinStyle
    }
    fun setLineJoinStyle(value: NSLineJoinStyle) {
        val sel = ObjCRuntime.sel("setLineJoinStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property windingRule
    fun windingRule(): NSWindingRule {
        val sel = ObjCRuntime.sel("windingRule")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindingRule
    }
    fun setWindingRule(value: NSWindingRule) {
        val sel = ObjCRuntime.sel("setWindingRule:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property miterLimit
    fun miterLimit(): CGFloat {
        val sel = ObjCRuntime.sel("miterLimit")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMiterLimit(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMiterLimit:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property flatness
    fun flatness(): CGFloat {
        val sel = ObjCRuntime.sel("flatness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setFlatness(value: CGFloat) {
        val sel = ObjCRuntime.sel("setFlatness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezierPathByFlatteningPath
    fun bezierPathByFlatteningPath(): MemorySegment {
        val sel = ObjCRuntime.sel("bezierPathByFlatteningPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bezierPathByReversingPath
    fun bezierPathByReversingPath(): MemorySegment {
        val sel = ObjCRuntime.sel("bezierPathByReversingPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property empty
    fun isEmpty(): BOOL {
        val sel = ObjCRuntime.sel("isEmpty")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property currentPoint
    fun currentPoint(): NSPoint {
        val sel = ObjCRuntime.sel("currentPoint")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property controlPointBounds
    fun controlPointBounds(): NSRect {
        val sel = ObjCRuntime.sel("controlPointBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property bounds
    fun bounds(): NSRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property elementCount
    fun elementCount(): NSInteger {
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

