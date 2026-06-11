/**
 * Kotlin/JVM wrapper for Objective-C class: NSCandidateListTouchBarItem
 * Superclass: NSTouchBarItem
 */
open class NSCandidateListTouchBarItem(ptr: MemorySegment) : NSTouchBarItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCandidateListTouchBarItem") }
        
    }
    
    fun updateWithInsertionPointVisibility(isVisible: BOOL): Unit {
        val sel = ObjCRuntime.sel("updateWithInsertionPointVisibility:")
        ObjCRuntime.msgSend(null, ptr, sel, isVisible)
    }
    
    fun setCandidates_forSelectedRange_inString(candidates: MemorySegment, selectedRange: NSRange, originalString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCandidates:forSelectedRange:inString:")
        ObjCRuntime.msgSend(null, ptr, sel, candidates, ObjCRuntime.ObjCStructArg(selectedRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), originalString)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setCandidates_forSelectedRange_inString(candidates: MemorySegment, selectedRange: NSRange, originalString: String): Unit = setCandidates_forSelectedRange_inString(candidates, selectedRange, ObjCRuntime.newNSString(Arena.global(), originalString))
    
    // @property client
    /** @return NSView<NSTextInputClient> * */
    fun client(): MemorySegment {
        val sel = ObjCRuntime.sel("client")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setClient(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setClient:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSCandidateListTouchBarItemDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collapsed
    fun isCollapsed(): BOOL {
        val sel = ObjCRuntime.sel("isCollapsed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCollapsed(value: BOOL) {
        val sel = ObjCRuntime.sel("setCollapsed:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsCollapsing
    fun allowsCollapsing(): BOOL {
        val sel = ObjCRuntime.sel("allowsCollapsing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsCollapsing(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsCollapsing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property candidateListVisible
    fun isCandidateListVisible(): BOOL {
        val sel = ObjCRuntime.sel("isCandidateListVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsTextInputContextCandidates
    fun allowsTextInputContextCandidates(): BOOL {
        val sel = ObjCRuntime.sel("allowsTextInputContextCandidates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsTextInputContextCandidates(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsTextInputContextCandidates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attributedStringForCandidate
    fun attributedStringForCandidate(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedStringForCandidate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedStringForCandidate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedStringForCandidate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property candidates
    /** @return NSArray<CandidateType> * */
    fun candidates(): MemorySegment {
        val sel = ObjCRuntime.sel("candidates")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property customizationLabel
    fun customizationLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("customizationLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomizationLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomizationLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun customizationLabelAsString(): String = ObjCRuntime.toJavaString(customizationLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCustomizationLabel(value: String) = setCustomizationLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
}

