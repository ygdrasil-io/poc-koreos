/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLConnection
 * Superclass: NSObject
 */
open class NSURLConnection(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLConnection") }
        
        fun connectionWithRequest_delegate(request: MemorySegment, delegate: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("connectionWithRequest:delegate:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, request, delegate) as MemorySegment
        }
        
        fun canHandleRequest(request: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canHandleRequest:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, request) as BOOL
        }
        
    }
    
    fun initWithRequest_delegate_startImmediately(request: MemorySegment, delegate: MemorySegment, startImmediately: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRequest:delegate:startImmediately:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, delegate, startImmediately) as MemorySegment
    }
    
    fun initWithRequest_delegate(request: MemorySegment, delegate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRequest:delegate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, request, delegate) as MemorySegment
    }
    
    fun start(): Unit {
        val sel = ObjCRuntime.sel("start")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun scheduleInRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun unscheduleFromRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("unscheduleFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun setDelegateQueue(queue: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDelegateQueue:")
        ObjCRuntime.msgSend(null, ptr, sel, queue)
    }
    
    // @property originalRequest
    fun originalRequest(): MemorySegment {
        val sel = ObjCRuntime.sel("originalRequest")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentRequest
    fun currentRequest(): MemorySegment {
        val sel = ObjCRuntime.sel("currentRequest")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

// ── Category: NSURLConnectionSynchronousLoading on NSURLConnection ─────────────────────────────────────────

// Class method: +[NSURLConnection sendSynchronousRequest:returningResponse:error:]
fun NSURLConnection_sendSynchronousRequest_returningResponse_error(request: MemorySegment, response: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sendSynchronousRequest:returningResponse:error:")
    val cls = ObjCRuntime.getClass("NSURLConnection")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, request, response, error) as MemorySegment
}

// ── Category: NSURLConnectionQueuedLoading on NSURLConnection ─────────────────────────────────────────

// Class method: +[NSURLConnection sendAsynchronousRequest:queue:completionHandler:]
fun NSURLConnection_sendAsynchronousRequest_queue_completionHandler(request: MemorySegment, queue: MemorySegment, handler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("sendAsynchronousRequest:queue:completionHandler:")
    val cls = ObjCRuntime.getClass("NSURLConnection")
    ObjCRuntime.msgSend(null, cls, sel, request, queue, handler)
}

