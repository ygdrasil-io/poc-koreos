/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLDownload
 * Superclass: NSObject
 */
open class NSURLDownload(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLDownload") }
        
        fun canResumeDownloadDecodedWithEncodingMIMEType(MIMEType: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canResumeDownloadDecodedWithEncodingMIMEType:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, MIMEType) as BOOL
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun canResumeDownloadDecodedWithEncodingMIMEType(MIMEType: String): BOOL = canResumeDownloadDecodedWithEncodingMIMEType(ObjCRuntime.newNSString(Arena.global(), MIMEType))
        
    }
    
    fun initWithRequest_delegate(request: MemorySegment, delegate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRequest:delegate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, delegate) as MemorySegment
    }
    
    fun initWithResumeData_delegate_path(resumeData: MemorySegment, delegate: MemorySegment, path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithResumeData:delegate:path:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, resumeData, delegate, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithResumeData_delegate_path(resumeData: MemorySegment, delegate: MemorySegment, path: String): MemorySegment = initWithResumeData_delegate_path(resumeData, delegate, ObjCRuntime.newNSString(Arena.global(), path))
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setDestination_allowOverwrite(path: MemorySegment, allowOverwrite: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDestination:allowOverwrite:")
        ObjCRuntime.msgSend(null, ptr, sel, path, allowOverwrite)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setDestination_allowOverwrite(path: String, allowOverwrite: BOOL): Unit = setDestination_allowOverwrite(ObjCRuntime.newNSString(Arena.global(), path), allowOverwrite)
    
    // @property request
    fun request(): MemorySegment {
        val sel = ObjCRuntime.sel("request")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property resumeData
    fun resumeData(): MemorySegment {
        val sel = ObjCRuntime.sel("resumeData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deletesFileUponFailure
    fun deletesFileUponFailure(): BOOL {
        val sel = ObjCRuntime.sel("deletesFileUponFailure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDeletesFileUponFailure(value: BOOL) {
        val sel = ObjCRuntime.sel("setDeletesFileUponFailure:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

