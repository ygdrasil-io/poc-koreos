/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextLayoutManager
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSTextSelectionDataSource
 */
open class NSTextLayoutManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextLayoutManager") }
        
        /** @return NSDictionary<NSAttributedStringKey,id> * */
        fun linkRenderingAttributes(): MemorySegment {
            val sel = ObjCRuntime.sel("linkRenderingAttributes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun replaceTextContentManager(textContentManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceTextContentManager:")
        ObjCRuntime.msgSend(null, ptr, sel, textContentManager)
    }
    
    fun ensureLayoutForRange(range: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForRange:")
        ObjCRuntime.msgSend(null, ptr, sel, range)
    }
    
    fun ensureLayoutForBounds(bounds: CGRect): Unit {
        val sel = ObjCRuntime.sel("ensureLayoutForBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(bounds, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun invalidateLayoutForRange(range: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateLayoutForRange:")
        ObjCRuntime.msgSend(null, ptr, sel, range)
    }
    
    fun textLayoutFragmentForPosition(position: CGPoint): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutFragmentForPosition:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(position, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    fun textLayoutFragmentForLocation(location: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutFragmentForLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location) as MemorySegment
    }
    
    /** @return id<NSTextLocation> */
    fun enumerateTextLayoutFragmentsFromLocation_options_usingBlock(location: MemorySegment, options: NSTextLayoutFragmentEnumerationOptions, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("enumerateTextLayoutFragmentsFromLocation:options:usingBlock:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, options, block) as MemorySegment
    }
    
    fun enumerateRenderingAttributesFromLocation_reverse_usingBlock(location: MemorySegment, reverse: BOOL, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateRenderingAttributesFromLocation:reverse:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, location, reverse, block)
    }
    
    fun setRenderingAttributes_forTextRange(renderingAttributes: MemorySegment, textRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setRenderingAttributes:forTextRange:")
        ObjCRuntime.msgSend(null, ptr, sel, renderingAttributes, textRange)
    }
    
    fun addRenderingAttribute_value_forTextRange(renderingAttribute: NSAttributedStringKey, value: MemorySegment, textRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addRenderingAttribute:value:forTextRange:")
        ObjCRuntime.msgSend(null, ptr, sel, renderingAttribute, value, textRange)
    }
    
    fun removeRenderingAttribute_forTextRange(renderingAttribute: NSAttributedStringKey, textRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeRenderingAttribute:forTextRange:")
        ObjCRuntime.msgSend(null, ptr, sel, renderingAttribute, textRange)
    }
    
    fun invalidateRenderingAttributesForTextRange(textRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateRenderingAttributesForTextRange:")
        ObjCRuntime.msgSend(null, ptr, sel, textRange)
    }
    
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    fun renderingAttributesForLink_atLocation(link: MemorySegment, location: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("renderingAttributesForLink:atLocation:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, link, location) as MemorySegment
    }
    
    fun enumerateTextSegmentsInRange_type_options_usingBlock(textRange: MemorySegment, type: NSTextLayoutManagerSegmentType, options: NSTextLayoutManagerSegmentOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateTextSegmentsInRange:type:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, textRange, type, options, block)
    }
    
    fun replaceContentsInRange_withTextElements(range: MemorySegment, textElements: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceContentsInRange:withTextElements:")
        ObjCRuntime.msgSend(null, ptr, sel, range, textElements)
    }
    
    fun replaceContentsInRange_withAttributedString(range: MemorySegment, attributedString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceContentsInRange:withAttributedString:")
        ObjCRuntime.msgSend(null, ptr, sel, range, attributedString)
    }
    
    // @property delegate
    /** @return id<NSTextLayoutManagerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesFontLeading
    fun usesFontLeading(): BOOL {
        val sel = ObjCRuntime.sel("usesFontLeading")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesFontLeading(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesFontLeading:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property limitsLayoutForSuspiciousContents
    fun limitsLayoutForSuspiciousContents(): BOOL {
        val sel = ObjCRuntime.sel("limitsLayoutForSuspiciousContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLimitsLayoutForSuspiciousContents(value: BOOL) {
        val sel = ObjCRuntime.sel("setLimitsLayoutForSuspiciousContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesHyphenation
    fun usesHyphenation(): BOOL {
        val sel = ObjCRuntime.sel("usesHyphenation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesHyphenation(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesHyphenation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property resolvesNaturalAlignmentWithBaseWritingDirection
    fun resolvesNaturalAlignmentWithBaseWritingDirection(): BOOL {
        val sel = ObjCRuntime.sel("resolvesNaturalAlignmentWithBaseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setResolvesNaturalAlignmentWithBaseWritingDirection(value: BOOL) {
        val sel = ObjCRuntime.sel("setResolvesNaturalAlignmentWithBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textContentManager
    fun textContentManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textContentManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textContainer
    fun textContainer(): MemorySegment {
        val sel = ObjCRuntime.sel("textContainer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextContainer(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usageBoundsForTextContainer
    fun usageBoundsForTextContainer(): CGRect {
        val sel = ObjCRuntime.sel("usageBoundsForTextContainer")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
    // @property textViewportLayoutController
    fun textViewportLayoutController(): MemorySegment {
        val sel = ObjCRuntime.sel("textViewportLayoutController")
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
    
    // @property textSelections
    /** @return NSArray<NSTextSelection *> * */
    fun textSelections(): MemorySegment {
        val sel = ObjCRuntime.sel("textSelections")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextSelections(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextSelections:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textSelectionNavigation
    fun textSelectionNavigation(): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionNavigation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextSelectionNavigation(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextSelectionNavigation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property renderingAttributesValidator
    fun renderingAttributesValidator(): MemorySegment {
        val sel = ObjCRuntime.sel("renderingAttributesValidator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRenderingAttributesValidator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRenderingAttributesValidator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property linkRenderingAttributes
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    fun linkRenderingAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("linkRenderingAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

