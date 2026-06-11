/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutDimension
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutDimension(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutDimension") }
        
        fun fractionalWidthDimension(fractionalWidth: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fractionalWidthDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fractionalWidth) as MemorySegment
        }
        
        fun fractionalHeightDimension(fractionalHeight: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fractionalHeightDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fractionalHeight) as MemorySegment
        }
        
        fun absoluteDimension(absoluteDimension: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("absoluteDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, absoluteDimension) as MemorySegment
        }
        
        fun estimatedDimension(estimatedDimension: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("estimatedDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, estimatedDimension) as MemorySegment
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
    
    // @property isFractionalWidth
    fun isFractionalWidth(): BOOL {
        val sel = ObjCRuntime.sel("isFractionalWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isFractionalHeight
    fun isFractionalHeight(): BOOL {
        val sel = ObjCRuntime.sel("isFractionalHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isAbsolute
    fun isAbsolute(): BOOL {
        val sel = ObjCRuntime.sel("isAbsolute")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isEstimated
    fun isEstimated(): BOOL {
        val sel = ObjCRuntime.sel("isEstimated")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property dimension
    fun dimension(): CGFloat {
        val sel = ObjCRuntime.sel("dimension")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

