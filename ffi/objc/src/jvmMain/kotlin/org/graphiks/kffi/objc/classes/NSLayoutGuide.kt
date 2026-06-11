/**
 * Kotlin/JVM wrapper for Objective-C class: NSLayoutGuide
 * Superclass: NSObject
 * Protocols: NSCoding, NSUserInterfaceItemIdentification
 */
open class NSLayoutGuide(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSLayoutGuide") }
        
    }
    
    /** @return NSArray<NSLayoutConstraint *> * */
    fun constraintsAffectingLayoutForOrientation(orientation: NSLayoutConstraintOrientation): MemorySegment {
        val sel = ObjCRuntime.sel("constraintsAffectingLayoutForOrientation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, orientation) as MemorySegment
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property owningView
    fun owningView(): MemorySegment {
        val sel = ObjCRuntime.sel("owningView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOwningView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOwningView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property identifier
    fun identifier(): NSUserInterfaceItemIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceItemIdentifier
    }
    fun setIdentifier(value: NSUserInterfaceItemIdentifier) {
        val sel = ObjCRuntime.sel("setIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property leadingAnchor
    fun leadingAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("leadingAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property trailingAnchor
    fun trailingAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("trailingAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property leftAnchor
    fun leftAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("leftAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rightAnchor
    fun rightAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("rightAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property topAnchor
    fun topAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("topAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bottomAnchor
    fun bottomAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("bottomAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property widthAnchor
    fun widthAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("widthAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property heightAnchor
    fun heightAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("heightAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property centerXAnchor
    fun centerXAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("centerXAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property centerYAnchor
    fun centerYAnchor(): MemorySegment {
        val sel = ObjCRuntime.sel("centerYAnchor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hasAmbiguousLayout
    fun hasAmbiguousLayout(): BOOL {
        val sel = ObjCRuntime.sel("hasAmbiguousLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

