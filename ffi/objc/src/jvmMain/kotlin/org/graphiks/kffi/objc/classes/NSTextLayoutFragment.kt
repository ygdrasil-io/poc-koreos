/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextLayoutFragment
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSTextLayoutFragment(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextLayoutFragment") }
        
    }
    
    fun initWithTextElement_range(textElement: MemorySegment, rangeInElement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTextElement:range:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textElement, rangeInElement) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun textLineFragmentForVerticalOffset_requiresExactMatch(verticalOffset: CGFloat, requiresExactMatch: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("textLineFragmentForVerticalOffset:requiresExactMatch:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, verticalOffset, requiresExactMatch) as MemorySegment
    }
    
    fun textLineFragmentForTextLocation_isUpstreamAffinity(textLocation: MemorySegment, isUpstreamAffinity: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("textLineFragmentForTextLocation:isUpstreamAffinity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textLocation, isUpstreamAffinity) as MemorySegment
    }
    
    fun invalidateLayout(): Unit {
        val sel = ObjCRuntime.sel("invalidateLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun drawAtPoint_inContext(point: CGPoint, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawAtPoint:inContext:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), context)
    }
    
    fun frameForTextAttachmentAtLocation(location: MemorySegment): CGRect {
        val sel = ObjCRuntime.sel("frameForTextAttachmentAtLocation:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, location) as CGRect
    }
    
    // @property textLayoutManager
    fun textLayoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textElement
    fun textElement(): MemorySegment {
        val sel = ObjCRuntime.sel("textElement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rangeInElement
    fun rangeInElement(): MemorySegment {
        val sel = ObjCRuntime.sel("rangeInElement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textLineFragments
    /** @return NSArray<NSTextLineFragment *> * */
    fun textLineFragments(): MemorySegment {
        val sel = ObjCRuntime.sel("textLineFragments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property layoutQueue
    fun layoutQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLayoutQueue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLayoutQueue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    fun state(): NSTextLayoutFragmentState {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextLayoutFragmentState
    }
    
    // @property layoutFragmentFrame
    fun layoutFragmentFrame(): CGRect {
        val sel = ObjCRuntime.sel("layoutFragmentFrame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property renderingSurfaceBounds
    fun renderingSurfaceBounds(): CGRect {
        val sel = ObjCRuntime.sel("renderingSurfaceBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property leadingPadding
    fun leadingPadding(): CGFloat {
        val sel = ObjCRuntime.sel("leadingPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property trailingPadding
    fun trailingPadding(): CGFloat {
        val sel = ObjCRuntime.sel("trailingPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property topMargin
    fun topMargin(): CGFloat {
        val sel = ObjCRuntime.sel("topMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property bottomMargin
    fun bottomMargin(): CGFloat {
        val sel = ObjCRuntime.sel("bottomMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property textAttachmentViewProviders
    /** @return NSArray<NSTextAttachmentViewProvider *> * */
    fun textAttachmentViewProviders(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttachmentViewProviders")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

