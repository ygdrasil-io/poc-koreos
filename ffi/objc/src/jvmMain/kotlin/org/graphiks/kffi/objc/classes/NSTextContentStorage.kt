/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextContentStorage
 * Superclass: NSTextContentManager
 * Protocols: NSTextStorageObserving
 */
open class NSTextContentStorage(ptr: MemorySegment) : NSTextContentManager(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextContentStorage") }
        
    }
    
    fun attributedStringForTextElement(textElement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("attributedStringForTextElement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textElement) as MemorySegment
    }
    
    fun textElementForAttributedString(attributedString: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textElementForAttributedString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributedString) as MemorySegment
    }
    
    /** @return id<NSTextLocation> */
    fun locationFromLocation_withOffset(location: MemorySegment, offset: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("locationFromLocation:withOffset:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, offset) as MemorySegment
    }
    
    fun offsetFromLocation_toLocation(from: MemorySegment, to: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("offsetFromLocation:toLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, from, to) as NSInteger
    }
    
    fun adjustedRangeFromRange_forEditingTextSelection(textRange: MemorySegment, forEditingTextSelection: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("adjustedRangeFromRange:forEditingTextSelection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRange, forEditingTextSelection) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSTextContentStorageDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property includesTextListMarkers
    fun includesTextListMarkers(): BOOL {
        val sel = ObjCRuntime.sel("includesTextListMarkers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIncludesTextListMarkers(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesTextListMarkers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attributedString
    fun attributedString(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

