/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutAnchor
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSLayoutAnchor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutAnchor") }
        
    }
    
    fun constraintEqualToAnchor(anchor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToAnchor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToAnchor(anchor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToAnchor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToAnchor(anchor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToAnchor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor) as MemorySegment
    }
    
    fun constraintEqualToAnchor_constant(anchor: MemorySegment, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintEqualToAnchor:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, c) as MemorySegment
    }
    
    fun constraintGreaterThanOrEqualToAnchor_constant(anchor: MemorySegment, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintGreaterThanOrEqualToAnchor:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, c) as MemorySegment
    }
    
    fun constraintLessThanOrEqualToAnchor_constant(anchor: MemorySegment, c: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("constraintLessThanOrEqualToAnchor:constant:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anchor, c) as MemorySegment
    }
    
    // @property name
    fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property item
    fun item(): MemorySegment {
        val sel = ObjCRuntime.sel("item")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hasAmbiguousLayout
    fun hasAmbiguousLayout(): BOOL {
        val sel = ObjCRuntime.sel("hasAmbiguousLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property constraintsAffectingLayout
    /** @return NSArray<NSLayoutConstraint *> * */
    fun constraintsAffectingLayout(): MemorySegment {
        val sel = ObjCRuntime.sel("constraintsAffectingLayout")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

