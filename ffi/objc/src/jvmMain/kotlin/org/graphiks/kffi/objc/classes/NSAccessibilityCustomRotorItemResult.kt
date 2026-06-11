/**
 * Kotlin/JVM wrapper for Objective-C class: NSAccessibilityCustomRotorItemResult
 * Superclass: NSObject
 */
open class NSAccessibilityCustomRotorItemResult(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAccessibilityCustomRotorItemResult") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithTargetElement(targetElement: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTargetElement:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, targetElement) as MemorySegment
    }
    
    fun initWithItemLoadingToken_customLabel(itemLoadingToken: MemorySegment, customLabel: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithItemLoadingToken:customLabel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemLoadingToken, customLabel) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithItemLoadingToken_customLabel(itemLoadingToken: MemorySegment, customLabel: String): MemorySegment = initWithItemLoadingToken_customLabel(itemLoadingToken, ObjCRuntime.newNSString(Arena.global(), customLabel))
    
    // @property targetElement
    /** @return id<NSAccessibilityElement> */
    fun targetElement(): MemorySegment {
        val sel = ObjCRuntime.sel("targetElement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property itemLoadingToken
    fun itemLoadingToken(): MemorySegment {
        val sel = ObjCRuntime.sel("itemLoadingToken")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property targetRange
    fun targetRange(): NSRange {
        val sel = ObjCRuntime.sel("targetRange")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    fun setTargetRange(value: NSRange) {
        val sel = ObjCRuntime.sel("setTargetRange:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    // @property customLabel
    fun customLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("customLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCustomLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCustomLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun customLabelAsString(): String = ObjCRuntime.toJavaString(customLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCustomLabel(value: String) = setCustomLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
}

