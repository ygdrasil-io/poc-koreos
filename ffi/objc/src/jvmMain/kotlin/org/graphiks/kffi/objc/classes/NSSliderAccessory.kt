/**
 * Kotlin/JVM wrapper for Objective-C class: NSSliderAccessory
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSSliderAccessory(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSliderAccessory") }
        
        fun accessoryWithImage(image: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("accessoryWithImage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, image) as MemorySegment
        }
        
    }
    
    // @property behavior
    fun behavior(): MemorySegment {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBehavior(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enabled
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category:  on NSSliderAccessory ─────────────────────────────────────────

