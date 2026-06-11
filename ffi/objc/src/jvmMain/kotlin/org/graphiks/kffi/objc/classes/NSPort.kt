/**
 * Kotlin/JVM wrapper for Objective-C class: NSPort
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPort(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPort") }
        
        fun port(): MemorySegment {
            val sel = ObjCRuntime.sel("port")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setDelegate(anObject: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, anObject)
    }
    
    /** @return id<NSPortDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun scheduleInRunLoop_forMode(runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("scheduleInRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, runLoop, mode)
    }
    
    fun removeFromRunLoop_forMode(runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeFromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, runLoop, mode)
    }
    
    fun sendBeforeDate_components_from_reserved(limitDate: MemorySegment, components: MemorySegment, receivePort: MemorySegment, headerSpaceReserved: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("sendBeforeDate:components:from:reserved:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, limitDate, components, receivePort, headerSpaceReserved) as BOOL
    }
    
    fun sendBeforeDate_msgid_components_from_reserved(limitDate: MemorySegment, msgID: NSUInteger, components: MemorySegment, receivePort: MemorySegment, headerSpaceReserved: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("sendBeforeDate:msgid:components:from:reserved:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, limitDate, msgID, components, receivePort, headerSpaceReserved) as BOOL
    }
    
    fun addConnection_toRunLoop_forMode(conn: MemorySegment, runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("addConnection:toRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, conn, runLoop, mode)
    }
    
    fun removeConnection_fromRunLoop_forMode(conn: MemorySegment, runLoop: MemorySegment, mode: NSRunLoopMode): Unit {
        val sel = ObjCRuntime.sel("removeConnection:fromRunLoop:forMode:")
        ObjCRuntime.msgSend(null, ptr, sel, conn, runLoop, mode)
    }
    
    // @property valid
    fun isValid(): BOOL {
        val sel = ObjCRuntime.sel("isValid")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property reservedSpaceLength
    fun reservedSpaceLength(): NSUInteger {
        val sel = ObjCRuntime.sel("reservedSpaceLength")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

