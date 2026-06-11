/**
 * Kotlin/JVM wrapper for Objective-C class: NSAttributedStringMarkdownParsingOptions
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSAttributedStringMarkdownParsingOptions(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAttributedStringMarkdownParsingOptions") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allowsExtendedAttributes
    fun allowsExtendedAttributes(): BOOL {
        val sel = ObjCRuntime.sel("allowsExtendedAttributes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsExtendedAttributes(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsExtendedAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property interpretedSyntax
    fun interpretedSyntax(): NSAttributedStringMarkdownInterpretedSyntax {
        val sel = ObjCRuntime.sel("interpretedSyntax")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAttributedStringMarkdownInterpretedSyntax
    }
    fun setInterpretedSyntax(value: NSAttributedStringMarkdownInterpretedSyntax) {
        val sel = ObjCRuntime.sel("setInterpretedSyntax:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property failurePolicy
    fun failurePolicy(): NSAttributedStringMarkdownParsingFailurePolicy {
        val sel = ObjCRuntime.sel("failurePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAttributedStringMarkdownParsingFailurePolicy
    }
    fun setFailurePolicy(value: NSAttributedStringMarkdownParsingFailurePolicy) {
        val sel = ObjCRuntime.sel("setFailurePolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property languageCode
    fun languageCode(): MemorySegment {
        val sel = ObjCRuntime.sel("languageCode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLanguageCode(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLanguageCode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun languageCodeAsString(): String = ObjCRuntime.toJavaString(languageCode())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLanguageCode(value: String) = setLanguageCode(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property appliesSourcePositionAttributes
    fun appliesSourcePositionAttributes(): BOOL {
        val sel = ObjCRuntime.sel("appliesSourcePositionAttributes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAppliesSourcePositionAttributes(value: BOOL) {
        val sel = ObjCRuntime.sel("setAppliesSourcePositionAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

