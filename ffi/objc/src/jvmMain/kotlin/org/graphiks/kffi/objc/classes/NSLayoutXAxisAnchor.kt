/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutXAxisAnchor
 * Superclass: NSLayoutAnchor
 */
open class NSLayoutXAxisAnchor(ptr: MemorySegment) : NSLayoutAnchor(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutXAxisAnchor") }
        
    }
    
    fun anchorWithOffsetToAnchor(otherAnchor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("anchorWithOffsetToAnchor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherAnchor) as MemorySegment
    }
    
    fun constraintEqualToSystemSpacingAfterAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToSystemSpacingAfterAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToSystemSpacingAfterAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToSystemSpacingAfterAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToSystemSpacingAfterAnchor_multiplier(anchor: MemorySegment, multiplier: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToSystemSpacingAfterAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, multiplier) as MemorySegment
    }
    
}

