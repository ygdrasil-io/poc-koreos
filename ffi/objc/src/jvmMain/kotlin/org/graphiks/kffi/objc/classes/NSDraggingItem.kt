/**
 * Kotlin/JVM wrapper for Objective-C class: NSDraggingItem
 * Superclass: NSObject
 */
open class NSDraggingItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDraggingItem") }
        
    }
    
    fun initWithPasteboardWriter(pasteboardWriter: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPasteboardWriter:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pasteboardWriter) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun setDraggingFrame_contents(frame: NSRect, contents: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDraggingFrame:contents:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), contents)
    }
    
    // @property item
    fun item(): MemorySegment {
        val sel = ObjCRuntime.sel("item")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property draggingFrame
    fun draggingFrame(): NSRect {
        val sel = ObjCRuntime.sel("draggingFrame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setDraggingFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setDraggingFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property imageComponentsProvider
    /** @return NSArray<NSDraggingImageComponent *> * _Nonnull (^)(void) */
    fun imageComponentsProvider(): MemorySegment {
        val sel = ObjCRuntime.sel("imageComponentsProvider")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImageComponentsProvider(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImageComponentsProvider:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageComponents
    /** @return NSArray<NSDraggingImageComponent *> * */
    fun imageComponents(): MemorySegment {
        val sel = ObjCRuntime.sel("imageComponents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

