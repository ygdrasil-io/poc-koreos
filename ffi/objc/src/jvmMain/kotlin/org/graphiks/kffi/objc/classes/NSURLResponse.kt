/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLResponse
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSURLResponse(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLResponse") }
        
    }
    
    fun initWithURL_MIMEType_expectedContentLength_textEncodingName(URL: MemorySegment, MIMEType: MemorySegment, length: NSInteger, name: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:MIMEType:expectedContentLength:textEncodingName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URL, MIMEType, length, name) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithURL_MIMEType_expectedContentLength_textEncodingName(URL: MemorySegment, MIMEType: String, length: NSInteger, name: String): MemorySegment = initWithURL_MIMEType_expectedContentLength_textEncodingName(URL, ObjCRuntime.newNSString(Arena.global(), MIMEType), length, ObjCRuntime.newNSString(Arena.global(), name))
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property MIMEType
    fun MIMEType(): MemorySegment {
        val sel = ObjCRuntime.sel("MIMEType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun MIMETypeAsString(): String = ObjCRuntime.toJavaString(MIMEType())
    
    // @property expectedContentLength
    fun expectedContentLength(): Long {
        val sel = ObjCRuntime.sel("expectedContentLength")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
    }
    
    // @property textEncodingName
    fun textEncodingName(): MemorySegment {
        val sel = ObjCRuntime.sel("textEncodingName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun textEncodingNameAsString(): String = ObjCRuntime.toJavaString(textEncodingName())
    
    // @property suggestedFilename
    fun suggestedFilename(): MemorySegment {
        val sel = ObjCRuntime.sel("suggestedFilename")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun suggestedFilenameAsString(): String = ObjCRuntime.toJavaString(suggestedFilename())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

