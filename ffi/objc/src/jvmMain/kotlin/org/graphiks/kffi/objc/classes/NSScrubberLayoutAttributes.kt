/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberLayoutAttributes
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSScrubberLayoutAttributes(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberLayoutAttributes") }
        
        fun layoutAttributesForItemAtIndex(index: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAttributesForItemAtIndex:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, index) as MemorySegment
        }
        
    }
    
    // @property itemIndex
    fun itemIndex(): NSInteger {
        val sel = ObjCRuntime.sel("itemIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setItemIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setItemIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property alpha
    fun alpha(): CGFloat {
        val sel = ObjCRuntime.sel("alpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setAlpha(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

