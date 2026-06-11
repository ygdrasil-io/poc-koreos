/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextAttachment
 * Superclass: NSObject
 * Protocols: NSTextAttachmentLayout, NSSecureCoding
 */
open class NSTextAttachment(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextAttachment") }
        
        fun textAttachmentViewProviderClassForFileType(fileType: MemorySegment): Class {
            val sel = ObjCRuntime.sel("textAttachmentViewProviderClassForFileType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fileType) as Class
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun textAttachmentViewProviderClassForFileType(fileType: String): Class = textAttachmentViewProviderClassForFileType(ObjCRuntime.newNSString(Arena.global(), fileType))
        
        fun registerTextAttachmentViewProviderClass_forFileType(textAttachmentViewProviderClass: Class, fileType: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("registerTextAttachmentViewProviderClass:forFileType:")
            ObjCRuntime.msgSend(null, _class, sel, textAttachmentViewProviderClass, fileType)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun registerTextAttachmentViewProviderClass_forFileType(textAttachmentViewProviderClass: Class, fileType: String): Unit = registerTextAttachmentViewProviderClass_forFileType(textAttachmentViewProviderClass, ObjCRuntime.newNSString(Arena.global(), fileType))
        
    }
    
    fun initWithData_ofType(contentData: MemorySegment, uti: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:ofType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, contentData, uti) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithData_ofType(contentData: MemorySegment, uti: String): MemorySegment = initWithData_ofType(contentData, ObjCRuntime.newNSString(Arena.global(), uti))
    
    fun initWithFileWrapper(fileWrapper: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFileWrapper:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileWrapper) as MemorySegment
    }
    
    // @property contents
    fun contents(): MemorySegment {
        val sel = ObjCRuntime.sel("contents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContents(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileType
    fun fileType(): MemorySegment {
        val sel = ObjCRuntime.sel("fileType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFileType(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFileType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun fileTypeAsString(): String = ObjCRuntime.toJavaString(fileType())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setFileType(value: String) = setFileType(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bounds
    fun bounds(): CGRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    fun setBounds(value: CGRect) {
        val sel = ObjCRuntime.sel("setBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property fileWrapper
    fun fileWrapper(): MemorySegment {
        val sel = ObjCRuntime.sel("fileWrapper")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFileWrapper(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFileWrapper:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attachmentCell
    /** @return id<NSTextAttachmentCell> */
    fun attachmentCell(): MemorySegment {
        val sel = ObjCRuntime.sel("attachmentCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttachmentCell(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttachmentCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineLayoutPadding
    fun lineLayoutPadding(): CGFloat {
        val sel = ObjCRuntime.sel("lineLayoutPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLineLayoutPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineLayoutPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsTextAttachmentView
    fun allowsTextAttachmentView(): BOOL {
        val sel = ObjCRuntime.sel("allowsTextAttachmentView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsTextAttachmentView(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsTextAttachmentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesTextAttachmentView
    fun usesTextAttachmentView(): BOOL {
        val sel = ObjCRuntime.sel("usesTextAttachmentView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSTextAttachment_Deprecation on NSTextAttachment ─────────────────────────────────────────

