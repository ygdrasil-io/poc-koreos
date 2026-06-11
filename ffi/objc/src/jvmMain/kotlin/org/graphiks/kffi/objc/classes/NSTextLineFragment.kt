/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextLineFragment
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSTextLineFragment(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextLineFragment") }
        
    }
    
    fun initWithAttributedString_range(attributedString: MemorySegment, range: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("initWithAttributedString:range:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributedString, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    fun initWithCoder(aDecoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aDecoder) as MemorySegment
    }
    
    fun initWithString_attributes_range(string: MemorySegment, attributes: MemorySegment, range: NSRange): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:attributes:range:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, attributes, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"))) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_attributes_range(string: String, attributes: MemorySegment, range: NSRange): MemorySegment = initWithString_attributes_range(ObjCRuntime.newNSString(Arena.global(), string), attributes, range)
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun drawAtPoint_inContext(point: CGPoint, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawAtPoint:inContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), context)
    }
    
    fun locationForCharacterAtIndex(index: NSInteger): CGPoint {
        val sel = ObjCRuntime.sel("locationForCharacterAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, index) as CGPoint
    }
    
    fun characterIndexForPoint(point: CGPoint): NSInteger {
        val sel = ObjCRuntime.sel("characterIndexForPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSInteger
    }
    
    fun fractionOfDistanceThroughGlyphForPoint(point: CGPoint): CGFloat {
        val sel = ObjCRuntime.sel("fractionOfDistanceThroughGlyphForPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as CGFloat
    }
    
    // @property attributedString
    fun attributedString(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property characterRange
    fun characterRange(): NSRange {
        val sel = ObjCRuntime.sel("characterRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property typographicBounds
    fun typographicBounds(): CGRect {
        val sel = ObjCRuntime.sel("typographicBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property glyphOrigin
    fun glyphOrigin(): CGPoint {
        val sel = ObjCRuntime.sel("glyphOrigin")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as CGPoint
    }
    
}

