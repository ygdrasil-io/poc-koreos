/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutYAxisAnchor
 * Superclass: NSLayoutAnchor
 */
open class NSLayoutYAxisAnchor(ptr: MemorySegment) : NSLayoutAnchor(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutYAxisAnchor") }
        
    }
    
    fun anchorWithOffsetToAnchor(otherAnchor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("anchorWithOffsetToAnchor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherAnchor) as MemorySegment
    }
    
    fun constraintEqualToSystemSpacingBelowAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToSystemSpacingBelowAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToSystemSpacingBelowAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToSystemSpacingBelowAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToSystemSpacingBelowAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToSystemSpacingBelowAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
}

