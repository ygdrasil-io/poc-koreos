/**
 * Kotlin/JVM wrapper for Objective-C class: NSDistributedLock
 * Superclass: NSObject
 */
open class NSDistributedLock(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDistributedLock") }
        
        fun lockWithPath(path: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("lockWithPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun lockWithPath(path: String): MemorySegment = lockWithPath(ObjCRuntime.newNSString(Arena.global(), path))
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithPath(path: String): MemorySegment = initWithPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun tryLock(): BOOL {
        val sel = ObjCRuntime.sel("tryLock")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun unlock(): Unit {
        val sel = ObjCRuntime.sel("unlock")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun breakLock(): Unit {
        val sel = ObjCRuntime.sel("breakLock")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property lockDate
    fun lockDate(): MemorySegment {
        val sel = ObjCRuntime.sel("lockDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

