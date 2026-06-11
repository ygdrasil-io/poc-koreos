/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextElement
 * Superclass: NSObject
 */
open class NSTextElement(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextElement") }
        
    }
    
    fun initWithTextContentManager(textContentManager: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTextContentManager:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textContentManager) as MemorySegment
    }
    
    // @property textContentManager
    fun textContentManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textContentManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextContentManager(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextContentManager:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property elementRange
    fun elementRange(): MemorySegment {
        val sel = ObjCRuntime.sel("elementRange")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setElementRange(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setElementRange:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property childElements
    /** @return NSArray<__kindof NSTextElement *> * */
    fun childElements(): MemorySegment {
        val sel = ObjCRuntime.sel("childElements")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property parentElement
    fun parentElement(): MemorySegment {
        val sel = ObjCRuntime.sel("parentElement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property isRepresentedElement
    fun isRepresentedElement(): BOOL {
        val sel = ObjCRuntime.sel("isRepresentedElement")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

