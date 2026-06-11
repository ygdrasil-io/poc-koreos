/**
 * Kotlin/JVM wrapper for Objective-C class: NSCachedImageRep
 * Superclass: NSImageRep
 */
open class NSCachedImageRep(ptr: MemorySegment) : NSImageRep(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCachedImageRep") }
        
    }
    
    fun initWithWindow_rect(win: MemorySegment, rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithWindow:rect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, win, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithSize_depth_separate_alpha(size: NSSize, depth: NSWindowDepth, flag: BOOL, alpha: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSize:depth:separate:alpha:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")), depth, flag, alpha) as MemorySegment
    }
    
    fun window(): MemorySegment {
        val sel = ObjCRuntime.sel("window")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun rect(): NSRect {
        val sel = ObjCRuntime.sel("rect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
}

