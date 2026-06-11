/**
 * Kotlin/JVM wrapper for Objective-C class: CIVector
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class CIVector(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("CIVector") }
        
        fun vectorWithValues_count(values: MemorySegment, count: size_t): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithValues:count:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, values, count) as MemorySegment
        }
        
        fun vectorWithX(x: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithX:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, x) as MemorySegment
        }
        
        fun vectorWithX_Y(x: CGFloat, y: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithX:Y:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, x, y) as MemorySegment
        }
        
        fun vectorWithX_Y_Z(x: CGFloat, y: CGFloat, z: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithX:Y:Z:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, x, y, z) as MemorySegment
        }
        
        fun vectorWithX_Y_Z_W(x: CGFloat, y: CGFloat, z: CGFloat, w: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithX:Y:Z:W:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, x, y, z, w) as MemorySegment
        }
        
        fun vectorWithCGPoint(p: CGPoint): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithCGPoint:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
        }
        
        fun vectorWithCGRect(r: CGRect): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithCGRect:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ObjCRuntime.ObjCStructArg(r, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
        }
        
        fun vectorWithCGAffineTransform(t: CGAffineTransform): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithCGAffineTransform:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, t) as MemorySegment
        }
        
        fun vectorWithString(representation: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("vectorWithString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, representation) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun vectorWithString(representation: String): MemorySegment = vectorWithString(ObjCRuntime.newNSString(Arena.global(), representation))
        
    }
    
    fun initWithValues_count(values: MemorySegment, count: size_t): MemorySegment {
        val sel = ObjCRuntime.sel("initWithValues:count:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, values, count) as MemorySegment
    }
    
    fun initWithX(x: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithX:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, x) as MemorySegment
    }
    
    fun initWithX_Y(x: CGFloat, y: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithX:Y:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, x, y) as MemorySegment
    }
    
    fun initWithX_Y_Z(x: CGFloat, y: CGFloat, z: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithX:Y:Z:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, x, y, z) as MemorySegment
    }
    
    fun initWithX_Y_Z_W(x: CGFloat, y: CGFloat, z: CGFloat, w: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithX:Y:Z:W:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, x, y, z, w) as MemorySegment
    }
    
    fun initWithCGPoint(p: CGPoint): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGPoint:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(p, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    fun initWithCGRect(r: CGRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(r, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithCGAffineTransform(t: CGAffineTransform): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGAffineTransform:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, t) as MemorySegment
    }
    
    fun initWithString(representation: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, representation) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString(representation: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), representation))
    
    fun valueAtIndex(index: size_t): CGFloat {
        val sel = ObjCRuntime.sel("valueAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, index) as CGFloat
    }
    
    // @property count
    fun count(): size_t {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as size_t
    }
    
    // @property X
    fun X(): CGFloat {
        val sel = ObjCRuntime.sel("X")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property Y
    fun Y(): CGFloat {
        val sel = ObjCRuntime.sel("Y")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property Z
    fun Z(): CGFloat {
        val sel = ObjCRuntime.sel("Z")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property W
    fun W(): CGFloat {
        val sel = ObjCRuntime.sel("W")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property CGPointValue
    fun CGPointValue(): CGPoint {
        val sel = ObjCRuntime.sel("CGPointValue")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as CGPoint
    }
    
    // @property CGRectValue
    fun CGRectValue(): CGRect {
        val sel = ObjCRuntime.sel("CGRectValue")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property CGAffineTransformValue
    fun CGAffineTransformValue(): CGAffineTransform {
        val sel = ObjCRuntime.sel("CGAffineTransformValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as CGAffineTransform
    }
    
    // @property stringRepresentation
    fun stringRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("stringRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringRepresentationAsString(): String = ObjCRuntime.toJavaString(stringRepresentation())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _count: size_t
    // ivar: _u: MemorySegment
}

