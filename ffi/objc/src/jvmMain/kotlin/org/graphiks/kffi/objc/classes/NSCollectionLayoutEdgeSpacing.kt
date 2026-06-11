/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutEdgeSpacing
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutEdgeSpacing(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutEdgeSpacing") }
        
        fun spacingForLeading_top_trailing_bottom(leading: MemorySegment, top: MemorySegment, trailing: MemorySegment, bottom: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("spacingForLeading:top:trailing:bottom:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, leading, top, trailing, bottom) as MemorySegment
        }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property leading
    fun leading(): MemorySegment {
        val sel = ObjCRuntime.sel("leading")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property top
    fun top(): MemorySegment {
        val sel = ObjCRuntime.sel("top")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property trailing
    fun trailing(): MemorySegment {
        val sel = ObjCRuntime.sel("trailing")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bottom
    fun bottom(): MemorySegment {
        val sel = ObjCRuntime.sel("bottom")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

