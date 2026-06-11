/**
 * Kotlin/JVM wrapper for Objective-C class: NSRotationGestureRecognizer
 * Superclass: NSGestureRecognizer
 */
open class NSRotationGestureRecognizer(ptr: MemorySegment) : NSGestureRecognizer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRotationGestureRecognizer") }
        
    }
    
    // @property rotation
    fun rotation(): CGFloat {
        val sel = ObjCRuntime.sel("rotation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRotation(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRotation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rotationInDegrees
    fun rotationInDegrees(): CGFloat {
        val sel = ObjCRuntime.sel("rotationInDegrees")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRotationInDegrees(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRotationInDegrees:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

