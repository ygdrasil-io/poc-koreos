/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberSelectionStyle
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScrubberSelectionStyle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberSelectionStyle") }
        
        fun outlineOverlayStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("outlineOverlayStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun roundedBackgroundStyle(): MemorySegment {
            val sel = ObjCRuntime.sel("roundedBackgroundStyle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun makeSelectionView(): MemorySegment {
        val sel = ObjCRuntime.sel("makeSelectionView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property outlineOverlayStyle
    fun outlineOverlayStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("outlineOverlayStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property roundedBackgroundStyle
    fun roundedBackgroundStyle(): MemorySegment {
        val sel = ObjCRuntime.sel("roundedBackgroundStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

