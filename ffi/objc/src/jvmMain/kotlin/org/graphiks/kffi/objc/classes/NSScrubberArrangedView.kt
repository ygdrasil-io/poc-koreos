/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberArrangedView
 * Superclass: NSView
 */
open class NSScrubberArrangedView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberArrangedView") }
        
    }
    
    fun applyLayoutAttributes(layoutAttributes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("applyLayoutAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, layoutAttributes)
    }
    
    // @property selected
    fun isSelected(): BOOL {
        val sel = ObjCRuntime.sel("isSelected")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelected(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelected:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlighted
    fun isHighlighted(): BOOL {
        val sel = ObjCRuntime.sel("isHighlighted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHighlighted(value: BOOL) {
        val sel = ObjCRuntime.sel("setHighlighted:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

