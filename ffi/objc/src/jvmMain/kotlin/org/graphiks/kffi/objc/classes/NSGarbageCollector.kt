/**
 * Kotlin/JVM wrapper for Objective-C class: NSGarbageCollector
 * Superclass: NSObject
 */
open class NSGarbageCollector(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGarbageCollector") }
        
        fun defaultCollector(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultCollector")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun isCollecting(): BOOL {
        val sel = ObjCRuntime.sel("isCollecting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun disable(): Unit {
        val sel = ObjCRuntime.sel("disable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun enable(): Unit {
        val sel = ObjCRuntime.sel("enable")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun collectIfNeeded(): Unit {
        val sel = ObjCRuntime.sel("collectIfNeeded")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun collectExhaustively(): Unit {
        val sel = ObjCRuntime.sel("collectExhaustively")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun disableCollectorForPointer(ptr: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("disableCollectorForPointer:")
        ObjCRuntime.msgSend(null, ptr, sel, ptr)
    }
    
    fun enableCollectorForPointer(ptr: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enableCollectorForPointer:")
        ObjCRuntime.msgSend(null, ptr, sel, ptr)
    }
    
    fun zone(): MemorySegment {
        val sel = ObjCRuntime.sel("zone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

