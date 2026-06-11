/**
 * Kotlin/JVM wrapper for Objective-C class: NSProxy
 * Protocols: NSObject
 */
open class NSProxy(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSProxy") }
        
        fun alloc(): MemorySegment {
            val sel = ObjCRuntime.sel("alloc")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun allocWithZone(zone: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("allocWithZone:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, zone) as MemorySegment
        }
        
        fun `class`(): Class {
            val sel = ObjCRuntime.sel("class")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class
        }
        
        fun respondsToSelector(aSelector: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("respondsToSelector:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, aSelector) as BOOL
        }
        
    }
    
    fun forwardInvocation(invocation: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("forwardInvocation:")
        ObjCRuntime.msgSend(null, ptr, sel, invocation)
    }
    
    fun methodSignatureForSelector(sel: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("methodSignatureForSelector:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sel) as MemorySegment
    }
    
    fun dealloc(): Unit {
        val sel = ObjCRuntime.sel("dealloc")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun finalize(): Unit {
        val sel = ObjCRuntime.sel("finalize")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun allowsWeakReference(): BOOL {
        val sel = ObjCRuntime.sel("allowsWeakReference")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun retainWeakReference(): BOOL {
        val sel = ObjCRuntime.sel("retainWeakReference")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property description
    fun description(): MemorySegment {
        val sel = ObjCRuntime.sel("description")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun descriptionAsString(): String = ObjCRuntime.toJavaString(description())
    
    // @property debugDescription
    fun debugDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("debugDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun debugDescriptionAsString(): String = ObjCRuntime.toJavaString(debugDescription())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: isa: MemorySegment
}

