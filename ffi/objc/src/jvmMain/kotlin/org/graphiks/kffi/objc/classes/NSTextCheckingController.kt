/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextCheckingController
 * Superclass: NSObject
 */
open class NSTextCheckingController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextCheckingController") }
        
    }
    
    fun initWithClient(client: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithClient:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, client) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun didChangeTextInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("didChangeTextInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun insertedTextInRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("insertedTextInRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun didChangeSelectedRange(): Unit {
        val sel = ObjCRuntime.sel("didChangeSelectedRange")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun considerTextCheckingForRange(range: NSRange): Unit {
        val sel = ObjCRuntime.sel("considerTextCheckingForRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun checkTextInRange_types_options(range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("checkTextInRange:types:options:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), checkingTypes, options)
    }
    
    fun checkTextInSelection(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("checkTextInSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun checkTextInDocument(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("checkTextInDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFrontSubstitutionsPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontSubstitutionsPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun checkSpelling(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("checkSpelling:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun showGuessPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showGuessPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun changeSpelling(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeSpelling:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun ignoreSpelling(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("ignoreSpelling:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun updateCandidates(): Unit {
        val sel = ObjCRuntime.sel("updateCandidates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    /** @return NSArray<NSAttributedStringKey> * */
    fun validAnnotations(): MemorySegment {
        val sel = ObjCRuntime.sel("validAnnotations")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun menuAtIndex_clickedOnSelection_effectiveRange(location: NSUInteger, clickedOnSelection: BOOL, effectiveRange: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("menuAtIndex:clickedOnSelection:effectiveRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, clickedOnSelection, effectiveRange) as MemorySegment
    }
    
    // @property client
    /** @return id<NSTextCheckingClient> */
    fun client(): MemorySegment {
        val sel = ObjCRuntime.sel("client")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property spellCheckerDocumentTag
    fun spellCheckerDocumentTag(): NSInteger {
        val sel = ObjCRuntime.sel("spellCheckerDocumentTag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setSpellCheckerDocumentTag(value: NSInteger) {
        val sel = ObjCRuntime.sel("setSpellCheckerDocumentTag:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

