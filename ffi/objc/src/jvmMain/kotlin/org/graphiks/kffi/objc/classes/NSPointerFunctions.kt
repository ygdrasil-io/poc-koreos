/**
 * Kotlin/JVM wrapper for Objective-C class: NSPointerFunctions
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSPointerFunctions(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPointerFunctions") }
        
        fun pointerFunctionsWithOptions(options: NSPointerFunctionsOptions): MemorySegment {
            val sel = ObjCRuntime.sel("pointerFunctionsWithOptions:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, options) as MemorySegment
        }
        
    }
    
    fun initWithOptions(options: NSPointerFunctionsOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    // @property hashFunction
    fun hashFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("hashFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHashFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHashFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property isEqualFunction
    fun isEqualFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("isEqualFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setIsEqualFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setIsEqualFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sizeFunction
    fun sizeFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("sizeFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSizeFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSizeFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property descriptionFunction
    fun descriptionFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("descriptionFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDescriptionFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDescriptionFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property relinquishFunction
    fun relinquishFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("relinquishFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRelinquishFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRelinquishFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property acquireFunction
    fun acquireFunction(): MemorySegment {
        val sel = ObjCRuntime.sel("acquireFunction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAcquireFunction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAcquireFunction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesStrongWriteBarrier
    fun usesStrongWriteBarrier(): BOOL {
        val sel = ObjCRuntime.sel("usesStrongWriteBarrier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesStrongWriteBarrier(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesStrongWriteBarrier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesWeakReadAndWriteBarriers
    fun usesWeakReadAndWriteBarriers(): BOOL {
        val sel = ObjCRuntime.sel("usesWeakReadAndWriteBarriers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesWeakReadAndWriteBarriers(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesWeakReadAndWriteBarriers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

