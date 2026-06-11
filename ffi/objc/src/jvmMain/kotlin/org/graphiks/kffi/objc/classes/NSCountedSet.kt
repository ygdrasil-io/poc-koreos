/**
 * Kotlin/JVM wrapper for Objective-C class: NSCountedSet
 * Superclass: NSMutableSet
 */
open class NSCountedSet(ptr: MemorySegment) : NSMutableSet(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCountedSet") }
        
    }
    
    fun initWithCapacity(numItems: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCapacity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numItems) as MemorySegment
    }
    
    fun initWithArray(array: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithArray:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, array) as MemorySegment
    }
    
    fun initWithSet(`set`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSet:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `set`) as MemorySegment
    }
    
    fun countForObject(`object`: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("countForObject:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, `object`) as NSUInteger
    }
    
    /** @return NSEnumerator<ObjectType> * */
    fun objectEnumerator(): MemorySegment {
        val sel = ObjCRuntime.sel("objectEnumerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun addObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
}

