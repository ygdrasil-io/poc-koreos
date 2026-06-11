/**
 * Kotlin/JVM wrapper for Objective-C class: NSPressGestureRecognizer
 * Superclass: NSGestureRecognizer
 * Protocols: NSCoding
 */
open class NSPressGestureRecognizer(ptr: MemorySegment) : NSGestureRecognizer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPressGestureRecognizer") }
        
    }
    
    // @property buttonMask
    fun buttonMask(): NSUInteger {
        val sel = ObjCRuntime.sel("buttonMask")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setButtonMask(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setButtonMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumPressDuration
    fun minimumPressDuration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("minimumPressDuration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setMinimumPressDuration(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setMinimumPressDuration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowableMovement
    fun allowableMovement(): CGFloat {
        val sel = ObjCRuntime.sel("allowableMovement")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setAllowableMovement(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAllowableMovement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfTouchesRequired
    fun numberOfTouchesRequired(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfTouchesRequired")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setNumberOfTouchesRequired(value: NSInteger) {
        val sel = ObjCRuntime.sel("setNumberOfTouchesRequired:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

