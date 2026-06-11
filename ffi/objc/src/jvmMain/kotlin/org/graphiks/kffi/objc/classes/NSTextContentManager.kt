/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextContentManager
 * Superclass: NSObject
 * Protocols: NSTextElementProvider, NSSecureCoding
 */
open class NSTextContentManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextContentManager") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun addTextLayoutManager(textLayoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addTextLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, textLayoutManager)
    }
    
    fun removeTextLayoutManager(textLayoutManager: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeTextLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, textLayoutManager)
    }
    
    fun synchronizeTextLayoutManagers(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("synchronizeTextLayoutManagers:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    /** @return NSArray<NSTextElement *> * */
    fun textElementsForRange(range: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textElementsForRange:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
    }
    
    fun performEditingTransactionUsingBlock(transaction: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performEditingTransactionUsingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, transaction)
    }
    
    fun recordEditActionInRange_newTextRange(originalTextRange: MemorySegment, newTextRange: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("recordEditActionInRange:newTextRange:")
        ObjCRuntime.msgSend(null, ptr, sel, originalTextRange, newTextRange)
    }
    
    // @property delegate
    /** @return id<NSTextContentManagerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textLayoutManagers
    /** @return NSArray<NSTextLayoutManager *> * */
    fun textLayoutManagers(): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutManagers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property primaryTextLayoutManager
    fun primaryTextLayoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("primaryTextLayoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrimaryTextLayoutManager(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrimaryTextLayoutManager:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasEditingTransaction
    fun hasEditingTransaction(): BOOL {
        val sel = ObjCRuntime.sel("hasEditingTransaction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticallySynchronizesTextLayoutManagers
    fun automaticallySynchronizesTextLayoutManagers(): BOOL {
        val sel = ObjCRuntime.sel("automaticallySynchronizesTextLayoutManagers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallySynchronizesTextLayoutManagers(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallySynchronizesTextLayoutManagers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticallySynchronizesToBackingStore
    fun automaticallySynchronizesToBackingStore(): BOOL {
        val sel = ObjCRuntime.sel("automaticallySynchronizesToBackingStore")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallySynchronizesToBackingStore(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallySynchronizesToBackingStore:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

