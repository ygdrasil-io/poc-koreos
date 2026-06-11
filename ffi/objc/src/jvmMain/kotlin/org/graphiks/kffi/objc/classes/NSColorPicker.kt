/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorPicker
 * Superclass: NSObject
 * Protocols: NSColorPickingDefault
 */
open class NSColorPicker(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorPicker") }
        
    }
    
    fun initWithPickerMask_colorPanel(mask: NSUInteger, owningColorPanel: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPickerMask:colorPanel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mask, owningColorPanel) as MemorySegment
    }
    
    fun insertNewButtonImage_in(newButtonImage: MemorySegment, buttonCell: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertNewButtonImage:in:")
        ObjCRuntime.msgSend(null, ptr, sel, newButtonImage, buttonCell)
    }
    
    fun viewSizeChanged(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("viewSizeChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun attachColorList(colorList: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("attachColorList:")
        ObjCRuntime.msgSend(null, ptr, sel, colorList)
    }
    
    fun detachColorList(colorList: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detachColorList:")
        ObjCRuntime.msgSend(null, ptr, sel, colorList)
    }
    
    fun setMode(mode: NSColorPanelMode): Unit {
        val sel = ObjCRuntime.sel("setMode:")
        ObjCRuntime.msgSend(null, ptr, sel, mode)
    }
    
    // @property colorPanel
    fun colorPanel(): MemorySegment {
        val sel = ObjCRuntime.sel("colorPanel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property provideNewButtonImage
    fun provideNewButtonImage(): MemorySegment {
        val sel = ObjCRuntime.sel("provideNewButtonImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property buttonToolTip
    fun buttonToolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("buttonToolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun buttonToolTipAsString(): String = ObjCRuntime.toJavaString(buttonToolTip())
    
    // @property minContentSize
    fun minContentSize(): NSSize {
        val sel = ObjCRuntime.sel("minContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
}

