/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableCellView
 * Superclass: NSView
 */
open class NSTableCellView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableCellView") }
        
    }
    
    // @property objectValue
    fun objectValue(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setObjectValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textField
    fun textField(): MemorySegment {
        val sel = ObjCRuntime.sel("textField")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextField(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextField:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageView
    fun imageView(): MemorySegment {
        val sel = ObjCRuntime.sel("imageView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImageView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImageView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundStyle
    fun backgroundStyle(): NSBackgroundStyle {
        val sel = ObjCRuntime.sel("backgroundStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
    }
    fun setBackgroundStyle(value: NSBackgroundStyle) {
        val sel = ObjCRuntime.sel("setBackgroundStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowSizeStyle
    fun rowSizeStyle(): NSTableViewRowSizeStyle {
        val sel = ObjCRuntime.sel("rowSizeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewRowSizeStyle
    }
    fun setRowSizeStyle(value: NSTableViewRowSizeStyle) {
        val sel = ObjCRuntime.sel("setRowSizeStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggingImageComponents
    /** @return NSArray<NSDraggingImageComponent *> * */
    fun draggingImageComponents(): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageComponents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

