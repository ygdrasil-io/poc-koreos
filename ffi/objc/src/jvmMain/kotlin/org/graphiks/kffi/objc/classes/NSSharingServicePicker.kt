/**
 * Kotlin/JVM wrapper for Objective-C class: NSSharingServicePicker
 * Superclass: NSObject
 */
open class NSSharingServicePicker(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSharingServicePicker") }
        
    }
    
    fun initWithItems(items: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithItems:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, items) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun showRelativeToRect_ofView_preferredEdge(rect: NSRect, view: MemorySegment, preferredEdge: NSRectEdge): Unit {
        val sel = ObjCRuntime.sel("showRelativeToRect:ofView:preferredEdge:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view, preferredEdge)
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSSharingServicePickerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardShareMenuItem
    fun standardShareMenuItem(): MemorySegment {
        val sel = ObjCRuntime.sel("standardShareMenuItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

