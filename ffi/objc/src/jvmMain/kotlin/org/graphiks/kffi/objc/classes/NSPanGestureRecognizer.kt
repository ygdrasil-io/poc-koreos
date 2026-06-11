/**
 * Kotlin/JVM wrapper for Objective-C class: NSPanGestureRecognizer
 * Superclass: NSGestureRecognizer
 * Protocols: NSCoding
 */
open class NSPanGestureRecognizer(ptr: MemorySegment) : NSGestureRecognizer(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPanGestureRecognizer") }
        
    }
    
    fun translationInView(view: MemorySegment): NSPoint {
        val sel = ObjCRuntime.sel("translationInView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, view) as NSPoint
    }
    
    fun setTranslation_inView(translation: NSPoint, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTranslation:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(translation, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), view)
    }
    
    fun velocityInView(view: MemorySegment): NSPoint {
        val sel = ObjCRuntime.sel("velocityInView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, view) as NSPoint
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

