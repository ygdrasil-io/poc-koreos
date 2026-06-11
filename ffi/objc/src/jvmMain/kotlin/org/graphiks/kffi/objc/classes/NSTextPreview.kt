/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextPreview
 * Superclass: NSObject
 */
open class NSTextPreview(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextPreview") }
        
    }
    
    fun initWithSnapshotImage_presentationFrame_candidateRects(snapshotImage: MemorySegment, presentationFrame: NSRect, candidateRects: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSnapshotImage:presentationFrame:candidateRects:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, snapshotImage, ObjCRuntime.ObjCStructArg(presentationFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), candidateRects) as MemorySegment
    }
    
    fun initWithSnapshotImage_presentationFrame(snapshotImage: MemorySegment, presentationFrame: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSnapshotImage:presentationFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, snapshotImage, ObjCRuntime.ObjCStructArg(presentationFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property previewImage
    fun previewImage(): MemorySegment {
        val sel = ObjCRuntime.sel("previewImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property presentationFrame
    fun presentationFrame(): NSRect {
        val sel = ObjCRuntime.sel("presentationFrame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property candidateRects
    /** @return NSArray<NSValue *> * */
    fun candidateRects(): MemorySegment {
        val sel = ObjCRuntime.sel("candidateRects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

