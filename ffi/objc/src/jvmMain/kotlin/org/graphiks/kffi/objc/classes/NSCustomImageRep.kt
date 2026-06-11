/**
 * Kotlin/JVM wrapper for Objective-C class: NSCustomImageRep
 * Superclass: NSImageRep
 */
open class NSCustomImageRep(ptr: MemorySegment) : NSImageRep(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCustomImageRep") }
        
    }
    
    fun initWithSize_flipped_drawingHandler(size: NSSize, drawingHandlerShouldBeCalledWithFlippedContext: BOOL, drawingHandler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSize:flipped:drawingHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), drawingHandlerShouldBeCalledWithFlippedContext, drawingHandler) as MemorySegment
    }
    
    fun initWithDrawSelector_delegate(selector: MemorySegment, delegate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDrawSelector:delegate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, selector, delegate) as MemorySegment
    }
    
    // @property drawingHandler
    fun drawingHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("drawingHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property drawSelector
    fun drawSelector(): MemorySegment {
        val sel = ObjCRuntime.sel("drawSelector")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

