/**
 * Kotlin/JVM wrapper for Objective-C class: NSStringDrawingContext
 * Superclass: NSObject
 */
open class NSStringDrawingContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStringDrawingContext") }
        
    }
    
    // @property minimumScaleFactor
    fun minimumScaleFactor(): CGFloat {
        val sel = ObjCRuntime.sel("minimumScaleFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumScaleFactor(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumScaleFactor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property actualScaleFactor
    fun actualScaleFactor(): CGFloat {
        val sel = ObjCRuntime.sel("actualScaleFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property totalBounds
    fun totalBounds(): CGRect {
        val sel = ObjCRuntime.sel("totalBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as CGRect
    }
    
}

