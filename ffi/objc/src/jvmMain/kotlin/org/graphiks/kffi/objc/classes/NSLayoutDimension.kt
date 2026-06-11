/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutDimension
 * Superclass: NSLayoutAnchor
 */
open class NSLayoutDimension(ptr: MemorySegment) : NSLayoutAnchor(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutDimension") }
        
    }
    
    fun constraintEqualToConstant(c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToConstant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, c) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToConstant(c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToConstant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, c) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToConstant(c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToConstant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, c) as MemorySegment
    }
    
    fun constraintEqualToAnchor_multiplier(anchor: MemorySegment, m: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToAnchor_multiplier(anchor: MemorySegment, m: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToAnchor_multiplier(anchor: MemorySegment, m: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToAnchor:multiplier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m) as MemorySegment
    }
    
    fun constraintEqualToAnchor_multiplier_constant(anchor: MemorySegment, m: CGFloat, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToAnchor:multiplier:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m, c) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToAnchor_multiplier_constant(anchor: MemorySegment, m: CGFloat, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToAnchor:multiplier:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m, c) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToAnchor_multiplier_constant(anchor: MemorySegment, m: CGFloat, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToAnchor:multiplier:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, m, c) as MemorySegment
    }
    
}

