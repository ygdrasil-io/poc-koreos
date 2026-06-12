package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: CALayer
 * Superclass: NSObject
 * Protocols: NSSecureCoding, CAMediaTiming
 */
open class CALayer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("CALayer") }
        
        open fun layer(): MemorySegment {
            val sel = ObjCRuntime.sel("layer")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun defaultValueForKey(key: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("defaultValueForKey:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, key) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun defaultValueForKey(key: String): MemorySegment = defaultValueForKey(ObjCRuntime.newNSString(Arena.global(), key))
        
        open fun needsDisplayForKey(key: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("needsDisplayForKey:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, key) as BOOL
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun needsDisplayForKey(key: String): BOOL = needsDisplayForKey(ObjCRuntime.newNSString(Arena.global(), key))
        
        open fun cornerCurveExpansionFactor(curve: CALayerCornerCurve): CGFloat {
            val sel = ObjCRuntime.sel("cornerCurveExpansionFactor:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, _class, sel, curve) as CGFloat
        }
        
        /** @return id<CAAction> */
        open fun defaultActionForKey(event: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("defaultActionForKey:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, event) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun defaultActionForKey(event: String): MemorySegment = defaultActionForKey(ObjCRuntime.newNSString(Arena.global(), event))
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithLayer(layer: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLayer:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, layer) as MemorySegment
    }
    
    open fun presentationLayer(): MemorySegment {
        val sel = ObjCRuntime.sel("presentationLayer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun modelLayer(): MemorySegment {
        val sel = ObjCRuntime.sel("modelLayer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun shouldArchiveValueForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("shouldArchiveValueForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun shouldArchiveValueForKey(key: String): BOOL = shouldArchiveValueForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    open fun affineTransform(): CGAffineTransform {
        val sel = ObjCRuntime.sel("affineTransform")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CGAffineTransform
    }
    
    open fun setAffineTransform(m: CGAffineTransform): Unit {
        val sel = ObjCRuntime.sel("setAffineTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, m)
    }
    
    open fun contentsAreFlipped(): BOOL {
        val sel = ObjCRuntime.sel("contentsAreFlipped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun removeFromSuperlayer(): Unit {
        val sel = ObjCRuntime.sel("removeFromSuperlayer")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun addSublayer(layer: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSublayer:")
        ObjCRuntime.msgSend(null, ptr, sel, layer)
    }
    
    open fun insertSublayer_atIndex(layer: MemorySegment, idx: Any): Unit {
        val sel = ObjCRuntime.sel("insertSublayer:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, layer, idx)
    }
    
    open fun insertSublayer_below(layer: MemorySegment, sibling: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertSublayer:below:")
        ObjCRuntime.msgSend(null, ptr, sel, layer, sibling)
    }
    
    open fun insertSublayer_above(layer: MemorySegment, sibling: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertSublayer:above:")
        ObjCRuntime.msgSend(null, ptr, sel, layer, sibling)
    }
    
    open fun replaceSublayer_with(oldLayer: MemorySegment, newLayer: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceSublayer:with:")
        ObjCRuntime.msgSend(null, ptr, sel, oldLayer, newLayer)
    }
    
    open fun convertPoint_fromLayer(p: CGPoint, l: MemorySegment): CGPoint {
        val sel = ObjCRuntime.sel("convertPoint:fromLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), l) as CGPoint
    }
    
    open fun convertPoint_toLayer(p: CGPoint, l: MemorySegment): CGPoint {
        val sel = ObjCRuntime.sel("convertPoint:toLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), l) as CGPoint
    }
    
    open fun convertRect_fromLayer(r: CGRect, l: MemorySegment): CGRect {
        val sel = ObjCRuntime.sel("convertRect:fromLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(r, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), l) as CGRect
    }
    
    open fun convertRect_toLayer(r: CGRect, l: MemorySegment): CGRect {
        val sel = ObjCRuntime.sel("convertRect:toLayer:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(r, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), l) as CGRect
    }
    
    open fun convertTime_fromLayer(t: CFTimeInterval, l: MemorySegment): CFTimeInterval {
        val sel = ObjCRuntime.sel("convertTime:fromLayer:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, t, l) as CFTimeInterval
    }
    
    open fun convertTime_toLayer(t: CFTimeInterval, l: MemorySegment): CFTimeInterval {
        val sel = ObjCRuntime.sel("convertTime:toLayer:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, t, l) as CFTimeInterval
    }
    
    open fun hitTest(p: CGPoint): MemorySegment {
        val sel = ObjCRuntime.sel("hitTest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    open fun containsPoint(p: CGPoint): BOOL {
        val sel = ObjCRuntime.sel("containsPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    open fun display(): Unit {
        val sel = ObjCRuntime.sel("display")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun setNeedsDisplay(): Unit {
        val sel = ObjCRuntime.sel("setNeedsDisplay")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun setNeedsDisplayInRect(r: CGRect): Unit {
        val sel = ObjCRuntime.sel("setNeedsDisplayInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(r, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun needsDisplay(): BOOL {
        val sel = ObjCRuntime.sel("needsDisplay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun displayIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("displayIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun drawInContext(ctx: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawInContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ctx)
    }
    
    open fun renderInContext(ctx: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("renderInContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ctx)
    }
    
    open fun preferredFrameSize(): CGSize {
        val sel = ObjCRuntime.sel("preferredFrameSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as CGSize
    }
    
    open fun setNeedsLayout(): Unit {
        val sel = ObjCRuntime.sel("setNeedsLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun needsLayout(): BOOL {
        val sel = ObjCRuntime.sel("needsLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun layoutIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("layoutIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun layoutSublayers(): Unit {
        val sel = ObjCRuntime.sel("layoutSublayers")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun resizeSublayersWithOldSize(size: CGSize): Unit {
        val sel = ObjCRuntime.sel("resizeSublayersWithOldSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    open fun resizeWithOldSuperlayerSize(size: CGSize): Unit {
        val sel = ObjCRuntime.sel("resizeWithOldSuperlayerSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    /** @return id<CAAction> */
    open fun actionForKey(event: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("actionForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun actionForKey(event: String): MemorySegment = actionForKey(ObjCRuntime.newNSString(Arena.global(), event))
    
    open fun addAnimation_forKey(anim: MemorySegment, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addAnimation:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, anim, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addAnimation_forKey(anim: MemorySegment, key: String): Unit = addAnimation_forKey(anim, ObjCRuntime.newNSString(Arena.global(), key))
    
    open fun removeAllAnimations(): Unit {
        val sel = ObjCRuntime.sel("removeAllAnimations")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun removeAnimationForKey(key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeAnimationForKey:")
        ObjCRuntime.msgSend(null, ptr, sel, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeAnimationForKey(key: String): Unit = removeAnimationForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    /** @return NSArray<NSString *> * */
    open fun animationKeys(): MemorySegment {
        val sel = ObjCRuntime.sel("animationKeys")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun animationForKey(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("animationForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun animationForKey(key: String): MemorySegment = animationForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    // @property bounds
    open fun bounds(): CGRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    open fun setBounds(value: CGRect) {
        val sel = ObjCRuntime.sel("setBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property position
    open fun position(): CGPoint {
        val sel = ObjCRuntime.sel("position")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as CGPoint
    }
    open fun setPosition(value: CGPoint) {
        val sel = ObjCRuntime.sel("setPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    // @property zPosition
    open fun zPosition(): CGFloat {
        val sel = ObjCRuntime.sel("zPosition")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setZPosition(value: CGFloat) {
        val sel = ObjCRuntime.sel("setZPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property anchorPoint
    open fun anchorPoint(): CGPoint {
        val sel = ObjCRuntime.sel("anchorPoint")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as CGPoint
    }
    open fun setAnchorPoint(value: CGPoint) {
        val sel = ObjCRuntime.sel("setAnchorPoint:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    // @property anchorPointZ
    open fun anchorPointZ(): CGFloat {
        val sel = ObjCRuntime.sel("anchorPointZ")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setAnchorPointZ(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAnchorPointZ:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property transform
    open fun transform(): CATransform3D {
        val sel = ObjCRuntime.sel("transform")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m13"), ValueLayout.JAVA_DOUBLE.withName("m14"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("m23"), ValueLayout.JAVA_DOUBLE.withName("m24"), ValueLayout.JAVA_DOUBLE.withName("m31"), ValueLayout.JAVA_DOUBLE.withName("m32"), ValueLayout.JAVA_DOUBLE.withName("m33"), ValueLayout.JAVA_DOUBLE.withName("m34"), ValueLayout.JAVA_DOUBLE.withName("m41"), ValueLayout.JAVA_DOUBLE.withName("m42"), ValueLayout.JAVA_DOUBLE.withName("m43"), ValueLayout.JAVA_DOUBLE.withName("m44")).withName("CATransform3D"), ptr, sel) as CATransform3D
    }
    open fun setTransform(value: CATransform3D) {
        val sel = ObjCRuntime.sel("setTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m13"), ValueLayout.JAVA_DOUBLE.withName("m14"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("m23"), ValueLayout.JAVA_DOUBLE.withName("m24"), ValueLayout.JAVA_DOUBLE.withName("m31"), ValueLayout.JAVA_DOUBLE.withName("m32"), ValueLayout.JAVA_DOUBLE.withName("m33"), ValueLayout.JAVA_DOUBLE.withName("m34"), ValueLayout.JAVA_DOUBLE.withName("m41"), ValueLayout.JAVA_DOUBLE.withName("m42"), ValueLayout.JAVA_DOUBLE.withName("m43"), ValueLayout.JAVA_DOUBLE.withName("m44")).withName("CATransform3D")))
    }
    
    // @property frame
    open fun frame(): CGRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    open fun setFrame(value: CGRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property hidden
    open fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleSided
    open fun isDoubleSided(): BOOL {
        val sel = ObjCRuntime.sel("isDoubleSided")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDoubleSided(value: BOOL) {
        val sel = ObjCRuntime.sel("setDoubleSided:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property geometryFlipped
    open fun isGeometryFlipped(): BOOL {
        val sel = ObjCRuntime.sel("isGeometryFlipped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setGeometryFlipped(value: BOOL) {
        val sel = ObjCRuntime.sel("setGeometryFlipped:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property superlayer
    open fun superlayer(): MemorySegment {
        val sel = ObjCRuntime.sel("superlayer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sublayers
    /** @return NSArray<__kindof CALayer *> * */
    open fun sublayers(): MemorySegment {
        val sel = ObjCRuntime.sel("sublayers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSublayers(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSublayers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sublayerTransform
    open fun sublayerTransform(): CATransform3D {
        val sel = ObjCRuntime.sel("sublayerTransform")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m13"), ValueLayout.JAVA_DOUBLE.withName("m14"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("m23"), ValueLayout.JAVA_DOUBLE.withName("m24"), ValueLayout.JAVA_DOUBLE.withName("m31"), ValueLayout.JAVA_DOUBLE.withName("m32"), ValueLayout.JAVA_DOUBLE.withName("m33"), ValueLayout.JAVA_DOUBLE.withName("m34"), ValueLayout.JAVA_DOUBLE.withName("m41"), ValueLayout.JAVA_DOUBLE.withName("m42"), ValueLayout.JAVA_DOUBLE.withName("m43"), ValueLayout.JAVA_DOUBLE.withName("m44")).withName("CATransform3D"), ptr, sel) as CATransform3D
    }
    open fun setSublayerTransform(value: CATransform3D) {
        val sel = ObjCRuntime.sel("setSublayerTransform:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("m11"), ValueLayout.JAVA_DOUBLE.withName("m12"), ValueLayout.JAVA_DOUBLE.withName("m13"), ValueLayout.JAVA_DOUBLE.withName("m14"), ValueLayout.JAVA_DOUBLE.withName("m21"), ValueLayout.JAVA_DOUBLE.withName("m22"), ValueLayout.JAVA_DOUBLE.withName("m23"), ValueLayout.JAVA_DOUBLE.withName("m24"), ValueLayout.JAVA_DOUBLE.withName("m31"), ValueLayout.JAVA_DOUBLE.withName("m32"), ValueLayout.JAVA_DOUBLE.withName("m33"), ValueLayout.JAVA_DOUBLE.withName("m34"), ValueLayout.JAVA_DOUBLE.withName("m41"), ValueLayout.JAVA_DOUBLE.withName("m42"), ValueLayout.JAVA_DOUBLE.withName("m43"), ValueLayout.JAVA_DOUBLE.withName("m44")).withName("CATransform3D")))
    }
    
    // @property mask
    open fun mask(): MemorySegment {
        val sel = ObjCRuntime.sel("mask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setMask(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property masksToBounds
    open fun masksToBounds(): BOOL {
        val sel = ObjCRuntime.sel("masksToBounds")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setMasksToBounds(value: BOOL) {
        val sel = ObjCRuntime.sel("setMasksToBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contents
    open fun contents(): MemorySegment {
        val sel = ObjCRuntime.sel("contents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setContents(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentsRect
    open fun contentsRect(): CGRect {
        val sel = ObjCRuntime.sel("contentsRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    open fun setContentsRect(value: CGRect) {
        val sel = ObjCRuntime.sel("setContentsRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property contentsGravity
    open fun contentsGravity(): CALayerContentsGravity {
        val sel = ObjCRuntime.sel("contentsGravity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CALayerContentsGravity
    }
    open fun setContentsGravity(value: CALayerContentsGravity) {
        val sel = ObjCRuntime.sel("setContentsGravity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentsScale
    open fun contentsScale(): CGFloat {
        val sel = ObjCRuntime.sel("contentsScale")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setContentsScale(value: CGFloat) {
        val sel = ObjCRuntime.sel("setContentsScale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentsCenter
    open fun contentsCenter(): CGRect {
        val sel = ObjCRuntime.sel("contentsCenter")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    open fun setContentsCenter(value: CGRect) {
        val sel = ObjCRuntime.sel("setContentsCenter:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property contentsFormat
    open fun contentsFormat(): CALayerContentsFormat {
        val sel = ObjCRuntime.sel("contentsFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CALayerContentsFormat
    }
    open fun setContentsFormat(value: CALayerContentsFormat) {
        val sel = ObjCRuntime.sel("setContentsFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsExtendedDynamicRangeContent
    open fun wantsExtendedDynamicRangeContent(): BOOL {
        val sel = ObjCRuntime.sel("wantsExtendedDynamicRangeContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setWantsExtendedDynamicRangeContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setWantsExtendedDynamicRangeContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property toneMapMode
    open fun toneMapMode(): CAToneMapMode {
        val sel = ObjCRuntime.sel("toneMapMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CAToneMapMode
    }
    open fun setToneMapMode(value: CAToneMapMode) {
        val sel = ObjCRuntime.sel("setToneMapMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredDynamicRange
    open fun preferredDynamicRange(): CADynamicRange {
        val sel = ObjCRuntime.sel("preferredDynamicRange")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CADynamicRange
    }
    open fun setPreferredDynamicRange(value: CADynamicRange) {
        val sel = ObjCRuntime.sel("setPreferredDynamicRange:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentsHeadroom
    open fun contentsHeadroom(): CGFloat {
        val sel = ObjCRuntime.sel("contentsHeadroom")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setContentsHeadroom(value: CGFloat) {
        val sel = ObjCRuntime.sel("setContentsHeadroom:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wantsDynamicContentScaling
    open fun wantsDynamicContentScaling(): BOOL {
        val sel = ObjCRuntime.sel("wantsDynamicContentScaling")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setWantsDynamicContentScaling(value: BOOL) {
        val sel = ObjCRuntime.sel("setWantsDynamicContentScaling:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minificationFilter
    open fun minificationFilter(): CALayerContentsFilter {
        val sel = ObjCRuntime.sel("minificationFilter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CALayerContentsFilter
    }
    open fun setMinificationFilter(value: CALayerContentsFilter) {
        val sel = ObjCRuntime.sel("setMinificationFilter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property magnificationFilter
    open fun magnificationFilter(): CALayerContentsFilter {
        val sel = ObjCRuntime.sel("magnificationFilter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CALayerContentsFilter
    }
    open fun setMagnificationFilter(value: CALayerContentsFilter) {
        val sel = ObjCRuntime.sel("setMagnificationFilter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minificationFilterBias
    open fun minificationFilterBias(): Float {
        val sel = ObjCRuntime.sel("minificationFilterBias")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setMinificationFilterBias(value: Float) {
        val sel = ObjCRuntime.sel("setMinificationFilterBias:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opaque
    open fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setOpaque(value: BOOL) {
        val sel = ObjCRuntime.sel("setOpaque:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property needsDisplayOnBoundsChange
    open fun needsDisplayOnBoundsChange(): BOOL {
        val sel = ObjCRuntime.sel("needsDisplayOnBoundsChange")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setNeedsDisplayOnBoundsChange(value: BOOL) {
        val sel = ObjCRuntime.sel("setNeedsDisplayOnBoundsChange:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsAsynchronously
    open fun drawsAsynchronously(): BOOL {
        val sel = ObjCRuntime.sel("drawsAsynchronously")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDrawsAsynchronously(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsAsynchronously:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property edgeAntialiasingMask
    open fun edgeAntialiasingMask(): CAEdgeAntialiasingMask {
        val sel = ObjCRuntime.sel("edgeAntialiasingMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CAEdgeAntialiasingMask
    }
    open fun setEdgeAntialiasingMask(value: CAEdgeAntialiasingMask) {
        val sel = ObjCRuntime.sel("setEdgeAntialiasingMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsEdgeAntialiasing
    open fun allowsEdgeAntialiasing(): BOOL {
        val sel = ObjCRuntime.sel("allowsEdgeAntialiasing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsEdgeAntialiasing(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsEdgeAntialiasing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    open fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cornerRadius
    open fun cornerRadius(): CGFloat {
        val sel = ObjCRuntime.sel("cornerRadius")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setCornerRadius(value: CGFloat) {
        val sel = ObjCRuntime.sel("setCornerRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maskedCorners
    open fun maskedCorners(): CACornerMask {
        val sel = ObjCRuntime.sel("maskedCorners")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CACornerMask
    }
    open fun setMaskedCorners(value: CACornerMask) {
        val sel = ObjCRuntime.sel("setMaskedCorners:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cornerCurve
    open fun cornerCurve(): CALayerCornerCurve {
        val sel = ObjCRuntime.sel("cornerCurve")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CALayerCornerCurve
    }
    open fun setCornerCurve(value: CALayerCornerCurve) {
        val sel = ObjCRuntime.sel("setCornerCurve:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property borderWidth
    open fun borderWidth(): CGFloat {
        val sel = ObjCRuntime.sel("borderWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setBorderWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBorderWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property borderColor
    open fun borderColor(): MemorySegment {
        val sel = ObjCRuntime.sel("borderColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBorderColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBorderColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opacity
    open fun opacity(): Float {
        val sel = ObjCRuntime.sel("opacity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setOpacity(value: Float) {
        val sel = ObjCRuntime.sel("setOpacity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsGroupOpacity
    open fun allowsGroupOpacity(): BOOL {
        val sel = ObjCRuntime.sel("allowsGroupOpacity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsGroupOpacity(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsGroupOpacity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property compositingFilter
    open fun compositingFilter(): MemorySegment {
        val sel = ObjCRuntime.sel("compositingFilter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setCompositingFilter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCompositingFilter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property filters
    open fun filters(): MemorySegment {
        val sel = ObjCRuntime.sel("filters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setFilters(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFilters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundFilters
    open fun backgroundFilters(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundFilters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBackgroundFilters(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundFilters:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldRasterize
    open fun shouldRasterize(): BOOL {
        val sel = ObjCRuntime.sel("shouldRasterize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setShouldRasterize(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldRasterize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rasterizationScale
    open fun rasterizationScale(): CGFloat {
        val sel = ObjCRuntime.sel("rasterizationScale")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setRasterizationScale(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRasterizationScale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadowColor
    open fun shadowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("shadowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setShadowColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShadowColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadowOpacity
    open fun shadowOpacity(): Float {
        val sel = ObjCRuntime.sel("shadowOpacity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setShadowOpacity(value: Float) {
        val sel = ObjCRuntime.sel("setShadowOpacity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadowOffset
    open fun shadowOffset(): CGSize {
        val sel = ObjCRuntime.sel("shadowOffset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as CGSize
    }
    open fun setShadowOffset(value: CGSize) {
        val sel = ObjCRuntime.sel("setShadowOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property shadowRadius
    open fun shadowRadius(): CGFloat {
        val sel = ObjCRuntime.sel("shadowRadius")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setShadowRadius(value: CGFloat) {
        val sel = ObjCRuntime.sel("setShadowRadius:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shadowPath
    open fun shadowPath(): MemorySegment {
        val sel = ObjCRuntime.sel("shadowPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setShadowPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShadowPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autoresizingMask
    open fun autoresizingMask(): CAAutoresizingMask {
        val sel = ObjCRuntime.sel("autoresizingMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CAAutoresizingMask
    }
    open fun setAutoresizingMask(value: CAAutoresizingMask) {
        val sel = ObjCRuntime.sel("setAutoresizingMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layoutManager
    /** @return id<CALayoutManager> */
    open fun layoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setLayoutManager(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property actions
    /** @return NSDictionary<NSString *,id<CAAction>> * */
    open fun actions(): MemorySegment {
        val sel = ObjCRuntime.sel("actions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setActions(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setActions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property delegate
    /** @return id<CALayerDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property style
    open fun style(): MemorySegment {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setStyle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

