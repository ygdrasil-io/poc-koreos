/**
 * Kotlin/JVM wrapper for Objective-C class: NSViewAnimation
 * Superclass: NSAnimation
 */
open class NSViewAnimation(ptr: MemorySegment) : NSAnimation(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSViewAnimation") }
        
    }
    
    fun initWithViewAnimations(viewAnimations: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithViewAnimations:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, viewAnimations) as MemorySegment
    }
    
    // @property viewAnimations
    /** @return NSArray<NSDictionary<NSViewAnimationKey,id> *> * */
    fun viewAnimations(): MemorySegment {
        val sel = ObjCRuntime.sel("viewAnimations")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setViewAnimations(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setViewAnimations:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

