/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextRange
 * Superclass: NSObject
 */
open class NSTextRange(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextRange") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithLocation_endLocation(location: MemorySegment, endLocation: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocation:endLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, endLocation) as MemorySegment
    }
    
    fun initWithLocation(location: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun isEqualToTextRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    fun containsLocation(location: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, location) as BOOL
    }
    
    fun containsRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    fun intersectsWithTextRange(textRange: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("intersectsWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textRange) as BOOL
    }
    
    fun textRangeByIntersectingWithTextRange(textRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textRangeByIntersectingWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRange) as MemorySegment
    }
    
    fun textRangeByFormingUnionWithTextRange(textRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textRangeByFormingUnionWithTextRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRange) as MemorySegment
    }
    
    // @property empty
    fun isEmpty(): BOOL {
        val sel = ObjCRuntime.sel("isEmpty")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property location
    /** @return id<NSTextLocation> */
    fun location(): MemorySegment {
        val sel = ObjCRuntime.sel("location")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property endLocation
    /** @return id<NSTextLocation> */
    fun endLocation(): MemorySegment {
        val sel = ObjCRuntime.sel("endLocation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

