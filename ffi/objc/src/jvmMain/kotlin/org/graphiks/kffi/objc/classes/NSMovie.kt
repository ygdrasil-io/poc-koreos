/**
 * Kotlin/JVM wrapper for Objective-C class: NSMovie
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSMovie(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMovie") }
        
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithMovie(movie: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithMovie:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, movie) as MemorySegment
    }
    
    fun QTMovie(): MemorySegment {
        val sel = ObjCRuntime.sel("QTMovie")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

