/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberProportionalLayout
 * Superclass: NSScrubberLayout
 */
open class NSScrubberProportionalLayout(ptr: MemorySegment) : NSScrubberLayout(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberProportionalLayout") }
        
    }
    
    fun initWithNumberOfVisibleItems(numberOfVisibleItems: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithNumberOfVisibleItems:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, numberOfVisibleItems) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property numberOfVisibleItems
    fun numberOfVisibleItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfVisibleItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfVisibleItems(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfVisibleItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

