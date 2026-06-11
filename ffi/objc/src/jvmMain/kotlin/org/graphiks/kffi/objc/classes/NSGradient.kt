/**
 * Kotlin/JVM wrapper for Objective-C class: NSGradient
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSGradient(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGradient") }
        
    }
    
    fun initWithStartingColor_endingColor(startingColor: MemorySegment, endingColor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartingColor:endingColor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startingColor, endingColor) as MemorySegment
    }
    
    fun initWithColors(colorArray: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithColors:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, colorArray) as MemorySegment
    }
    
    fun initWithColorsAndLocations(firstColor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithColorsAndLocations:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, firstColor) as MemorySegment
    }
    
    fun initWithColors_atLocations_colorSpace(colorArray: MemorySegment, locations: MemorySegment, colorSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithColors:atLocations:colorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, colorArray, locations, colorSpace) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun drawFromPoint_toPoint_options(startingPoint: NSPoint, endingPoint: NSPoint, options: NSGradientDrawingOptions): Unit {
        val sel = ObjCRuntime.sel("drawFromPoint:toPoint:options:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(startingPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(endingPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), options)
    }
    
    fun drawInRect_angle(rect: NSRect, angle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("drawInRect:angle:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), angle)
    }
    
    fun drawInBezierPath_angle(path: MemorySegment, angle: CGFloat): Unit {
        val sel = ObjCRuntime.sel("drawInBezierPath:angle:")
        ObjCRuntime.msgSend(null, ptr, sel, path, angle)
    }
    
    fun drawFromCenter_radius_toCenter_radius_options(startCenter: NSPoint, startRadius: CGFloat, endCenter: NSPoint, endRadius: CGFloat, options: NSGradientDrawingOptions): Unit {
        val sel = ObjCRuntime.sel("drawFromCenter:radius:toCenter:radius:options:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(startCenter, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), startRadius, ObjCRuntime.ObjCStructArg(endCenter, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), endRadius, options)
    }
    
    fun drawInRect_relativeCenterPosition(rect: NSRect, relativeCenterPosition: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawInRect:relativeCenterPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(relativeCenterPosition, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun drawInBezierPath_relativeCenterPosition(path: MemorySegment, relativeCenterPosition: NSPoint): Unit {
        val sel = ObjCRuntime.sel("drawInBezierPath:relativeCenterPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, path, ObjCRuntime.ObjCStructArg(relativeCenterPosition, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")))
    }
    
    fun getColor_location_atIndex(color: MemorySegment, location: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("getColor:location:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, color, location, index)
    }
    
    fun interpolatedColorAtLocation(location: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("interpolatedColorAtLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location) as MemorySegment
    }
    
    // @property colorSpace
    fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfColorStops
    fun numberOfColorStops(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColorStops")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

