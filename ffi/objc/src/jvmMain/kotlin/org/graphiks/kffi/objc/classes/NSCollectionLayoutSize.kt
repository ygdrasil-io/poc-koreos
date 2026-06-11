/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutSize
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutSize(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutSize") }
        
        fun sizeWithWidthDimension_heightDimension(width: MemorySegment, height: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sizeWithWidthDimension:heightDimension:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, width, height) as MemorySegment
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
    
    // @property widthDimension
    fun widthDimension(): MemorySegment {
        val sel = ObjCRuntime.sel("widthDimension")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property heightDimension
    fun heightDimension(): MemorySegment {
        val sel = ObjCRuntime.sel("heightDimension")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

