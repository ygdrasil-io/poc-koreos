/**
 * Kotlin/JVM wrapper for Objective-C class: NSStream
 * Superclass: NSObject
 */
open class NSStream(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStream") }
        
    }
    
    fun open(): Unit {
        val sel = ObjCRuntime.sel("open")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun propertyForKey(key: NSStreamPropertyKey): MemorySegment {
        val sel = ObjCRuntime.sel("propertyForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    fun setProperty_forKey(property: MemorySegment, key: NSStreamPropertyKey): BOOL {
        val sel = ObjCRuntime.sel("setProperty:forKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, property, key) as BOOL
    }
    
    fun scheduleInRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    fun removeFromRunLoop_forMode(aRunLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, aRunLoop, mode)
    }
    
    // @property delegate
    /** @return id<NSStreamDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property streamStatus
    fun streamStatus(): NSStreamStatus {
        val sel = ObjCRuntime.sel("streamStatus")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSStreamStatus
    }
    
    // @property streamError
    fun streamError(): MemorySegment {
        val sel = ObjCRuntime.sel("streamError")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSSocketStreamCreationExtensions on NSStream ─────────────────────────────────────────

// Class method: +[NSStream getStreamsToHostWithName:port:inputStream:outputStream:]
fun NSStream_getStreamsToHostWithName_port_inputStream_outputStream(hostname: MemorySegment, port: NSInteger, inputStream: MemorySegment, outputStream: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getStreamsToHostWithName:port:inputStream:outputStream:")
    val cls = ObjCRuntime.getClass("NSStream")
    ObjCRuntime.msgSend(null, cls, sel, hostname, port, inputStream, outputStream)
}

// Class method: +[NSStream getStreamsToHost:port:inputStream:outputStream:]
fun NSStream_getStreamsToHost_port_inputStream_outputStream(host: MemorySegment, port: NSInteger, inputStream: MemorySegment, outputStream: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getStreamsToHost:port:inputStream:outputStream:")
    val cls = ObjCRuntime.getClass("NSStream")
    ObjCRuntime.msgSend(null, cls, sel, host, port, inputStream, outputStream)
}

// ── Category: NSStreamBoundPairCreationExtensions on NSStream ─────────────────────────────────────────

// Class method: +[NSStream getBoundStreamsWithBufferSize:inputStream:outputStream:]
fun NSStream_getBoundStreamsWithBufferSize_inputStream_outputStream(bufferSize: NSUInteger, inputStream: MemorySegment, outputStream: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getBoundStreamsWithBufferSize:inputStream:outputStream:")
    val cls = ObjCRuntime.getClass("NSStream")
    ObjCRuntime.msgSend(null, cls, sel, bufferSize, inputStream, outputStream)
}

