/**
 * Kotlin/JVM wrapper for Objective-C class: NSOutputStream
 * Superclass: NSStream
 */
open class NSOutputStream(ptr: MemorySegment) : NSStream(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOutputStream") }
        
    }
    
    fun write_maxLength(buffer: MemorySegment, len: NSUInteger): NSInteger {
        val sel = ObjCRuntime.sel("write:maxLength:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, buffer, len) as NSInteger
    }
    
    fun initToMemory(): MemorySegment {
        val sel = ObjCRuntime.sel("initToMemory")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initToBuffer_capacity(buffer: MemorySegment, capacity: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initToBuffer:capacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, buffer, capacity) as MemorySegment
    }
    
    fun initWithURL_append(url: MemorySegment, shouldAppend: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:append:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, shouldAppend) as MemorySegment
    }
    
    // @property hasSpaceAvailable
    fun hasSpaceAvailable(): BOOL {
        val sel = ObjCRuntime.sel("hasSpaceAvailable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSOutputStreamExtensions on NSOutputStream ─────────────────────────────────────────

fun NSOutputStream.initToFileAtPath_append(path: MemorySegment, shouldAppend: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initToFileAtPath:append:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, shouldAppend) as MemorySegment
}

// Class method: +[NSOutputStream outputStreamToMemory]
fun NSOutputStream_outputStreamToMemory(): MemorySegment {
    val sel = ObjCRuntime.sel("outputStreamToMemory")
    val cls = ObjCRuntime.getClass("NSOutputStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSOutputStream outputStreamToBuffer:capacity:]
fun NSOutputStream_outputStreamToBuffer_capacity(buffer: MemorySegment, capacity: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("outputStreamToBuffer:capacity:")
    val cls = ObjCRuntime.getClass("NSOutputStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, buffer, capacity) as MemorySegment
}

// Class method: +[NSOutputStream outputStreamToFileAtPath:append:]
fun NSOutputStream_outputStreamToFileAtPath_append(path: MemorySegment, shouldAppend: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("outputStreamToFileAtPath:append:")
    val cls = ObjCRuntime.getClass("NSOutputStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path, shouldAppend) as MemorySegment
}

// Class method: +[NSOutputStream outputStreamWithURL:append:]
fun NSOutputStream_outputStreamWithURL_append(url: MemorySegment, shouldAppend: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("outputStreamWithURL:append:")
    val cls = ObjCRuntime.getClass("NSOutputStream")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, shouldAppend) as MemorySegment
}

