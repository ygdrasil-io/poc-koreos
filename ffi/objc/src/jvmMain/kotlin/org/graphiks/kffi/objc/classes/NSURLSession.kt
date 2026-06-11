/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLSession
 * Superclass: NSObject
 */
open class NSURLSession(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLSession") }
        
        fun sessionWithConfiguration(configuration: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sessionWithConfiguration:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, configuration) as MemorySegment
        }
        
        fun sessionWithConfiguration_delegate_delegateQueue(configuration: MemorySegment, delegate: MemorySegment, queue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sessionWithConfiguration:delegate:delegateQueue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, configuration, delegate, queue) as MemorySegment
        }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun sharedSession(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedSession")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun finishTasksAndInvalidate(): Unit {
        val sel = ObjCRuntime.sel("finishTasksAndInvalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidateAndCancel(): Unit {
        val sel = ObjCRuntime.sel("invalidateAndCancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resetWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("resetWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun flushWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("flushWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun getTasksWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getTasksWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun getAllTasksWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getAllTasksWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun dataTaskWithRequest(request: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dataTaskWithRequest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request) as MemorySegment
    }
    
    fun dataTaskWithURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dataTaskWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun uploadTaskWithRequest_fromFile(request: MemorySegment, fileURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("uploadTaskWithRequest:fromFile:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, fileURL) as MemorySegment
    }
    
    fun uploadTaskWithRequest_fromData(request: MemorySegment, bodyData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("uploadTaskWithRequest:fromData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, bodyData) as MemorySegment
    }
    
    fun uploadTaskWithResumeData(resumeData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("uploadTaskWithResumeData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, resumeData) as MemorySegment
    }
    
    fun uploadTaskWithStreamedRequest(request: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("uploadTaskWithStreamedRequest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request) as MemorySegment
    }
    
    fun downloadTaskWithRequest(request: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("downloadTaskWithRequest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request) as MemorySegment
    }
    
    fun downloadTaskWithURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("downloadTaskWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun downloadTaskWithResumeData(resumeData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("downloadTaskWithResumeData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, resumeData) as MemorySegment
    }
    
    fun streamTaskWithHostName_port(hostname: MemorySegment, port: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("streamTaskWithHostName:port:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, hostname, port) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun streamTaskWithHostName_port(hostname: String, port: NSInteger): MemorySegment = streamTaskWithHostName_port(ObjCRuntime.newNSString(Arena.global(), hostname), port)
    
    fun streamTaskWithNetService(service: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("streamTaskWithNetService:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, service) as MemorySegment
    }
    
    fun webSocketTaskWithURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("webSocketTaskWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun webSocketTaskWithURL_protocols(url: MemorySegment, protocols: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("webSocketTaskWithURL:protocols:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, protocols) as MemorySegment
    }
    
    fun webSocketTaskWithRequest(request: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("webSocketTaskWithRequest:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sharedSession
    fun sharedSession(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedSession")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegateQueue
    fun delegateQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("delegateQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSURLSessionDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property configuration
    fun configuration(): MemorySegment {
        val sel = ObjCRuntime.sel("configuration")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sessionDescription
    fun sessionDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("sessionDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSessionDescription(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSessionDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun sessionDescriptionAsString(): String = ObjCRuntime.toJavaString(sessionDescription())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSessionDescription(value: String) = setSessionDescription(ObjCRuntime.newNSString(Arena.global(), value))
    
}

// ── Category: NSURLSessionAsynchronousConvenience on NSURLSession ─────────────────────────────────────────

fun NSURLSession.dataTaskWithRequest_completionHandler(request: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dataTaskWithRequest:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, completionHandler) as MemorySegment
}

fun NSURLSession.dataTaskWithURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dataTaskWithURL:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, completionHandler) as MemorySegment
}

fun NSURLSession.uploadTaskWithRequest_fromFile_completionHandler(request: MemorySegment, fileURL: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("uploadTaskWithRequest:fromFile:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, fileURL, completionHandler) as MemorySegment
}

fun NSURLSession.uploadTaskWithRequest_fromData_completionHandler(request: MemorySegment, bodyData: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("uploadTaskWithRequest:fromData:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, bodyData, completionHandler) as MemorySegment
}

fun NSURLSession.uploadTaskWithResumeData_completionHandler(resumeData: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("uploadTaskWithResumeData:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, resumeData, completionHandler) as MemorySegment
}

fun NSURLSession.downloadTaskWithRequest_completionHandler(request: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("downloadTaskWithRequest:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, completionHandler) as MemorySegment
}

fun NSURLSession.downloadTaskWithURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("downloadTaskWithURL:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, completionHandler) as MemorySegment
}

fun NSURLSession.downloadTaskWithResumeData_completionHandler(resumeData: MemorySegment, completionHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("downloadTaskWithResumeData:completionHandler:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, resumeData, completionHandler) as MemorySegment
}

