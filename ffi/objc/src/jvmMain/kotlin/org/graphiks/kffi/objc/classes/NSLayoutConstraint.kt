/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutConstraint
 * Superclass: NSObject
 */
open class NSLayoutConstraint(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutConstraint") }
        
        /** @return NSArray<NSLayoutConstraint *> * */
        fun constraintsWithVisualFormat_options_metrics_views(format: MemorySegment, opts: NSLayoutFormatOptions, metrics: MemorySegment, views: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("constraintsWithVisualFormat:options:metrics:views:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, format, opts, metrics, views) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun constraintsWithVisualFormat_options_metrics_views(format: String, opts: NSLayoutFormatOptions, metrics: MemorySegment, views: MemorySegment): MemorySegment = constraintsWithVisualFormat_options_metrics_views(ObjCRuntime.newNSString(Arena.global(), format), opts, metrics, views)
        
        fun constraintWithItem_attribute_relatedBy_toItem_attribute_multiplier_constant(view1: MemorySegment, attr1: NSLayoutAttribute, relation: NSLayoutRelation, view2: MemorySegment, attr2: NSLayoutAttribute, multiplier: CGFloat, c: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("constraintWithItem:attribute:relatedBy:toItem:attribute:multiplier:constant:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, view1, attr1, relation, view2, attr2, multiplier, c) as MemorySegment
        }
        
        fun activateConstraints(constraints: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("activateConstraints:")
            ObjCRuntime.msgSend(null, _class, sel, constraints)
        }
        
        fun deactivateConstraints(constraints: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("deactivateConstraints:")
            ObjCRuntime.msgSend(null, _class, sel, constraints)
        }
        
    }
    
    // @property priority
    fun priority(): NSLayoutPriority {
        val sel = ObjCRuntime.sel("priority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSLayoutPriority
    }
    fun setPriority(value: NSLayoutPriority) {
        val sel = ObjCRuntime.sel("setPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldBeArchived
    fun shouldBeArchived(): BOOL {
        val sel = ObjCRuntime.sel("shouldBeArchived")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldBeArchived(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldBeArchived:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property firstItem
    fun firstItem(): MemorySegment {
        val sel = ObjCRuntime.sel("firstItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secondItem
    fun secondItem(): MemorySegment {
        val sel = ObjCRuntime.sel("secondItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property firstAttribute
    fun firstAttribute(): NSLayoutAttribute {
        val sel = ObjCRuntime.sel("firstAttribute")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLayoutAttribute
    }
    
    // @property secondAttribute
    fun secondAttribute(): NSLayoutAttribute {
        val sel = ObjCRuntime.sel("secondAttribute")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLayoutAttribute
    }
    
    // @property firstAnchor
    fun firstAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("firstAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secondAnchor
    fun secondAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("secondAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property relation
    fun relation(): NSLayoutRelation {
        val sel = ObjCRuntime.sel("relation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLayoutRelation
    }
    
    // @property multiplier
    fun multiplier(): CGFloat {
        val sel = ObjCRuntime.sel("multiplier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property constant
    fun constant(): CGFloat {
        val sel = ObjCRuntime.sel("constant")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setConstant(value: CGFloat) {
        val sel = ObjCRuntime.sel("setConstant:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property active
    fun isActive(): BOOL {
        val sel = ObjCRuntime.sel("isActive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setActive(value: BOOL) {
        val sel = ObjCRuntime.sel("setActive:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSIdentifier on NSLayoutConstraint ─────────────────────────────────────────

fun NSLayoutConstraint.identifier(): MemorySegment {
    val sel = ObjCRuntime.sel("identifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSLayoutConstraint.setIdentifier(identifier: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setIdentifier:")
    ObjCRuntime.msgSend(null, ptr, sel, identifier)
}

// @property identifier
fun NSLayoutConstraint.identifier(): MemorySegment {
    val sel = ObjCRuntime.sel("identifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSLayoutConstraint.setIdentifier(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setIdentifier:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category:  on NSLayoutConstraint ─────────────────────────────────────────

