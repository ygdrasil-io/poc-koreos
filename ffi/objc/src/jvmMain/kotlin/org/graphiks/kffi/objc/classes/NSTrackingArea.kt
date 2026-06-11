/**
 * Kotlin/JVM wrapper for Objective-C class: NSTrackingArea
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSTrackingArea(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTrackingArea") }
        
    }
    
    fun initWithRect_options_owner_userInfo(rect: NSRect, options: NSTrackingAreaOptions, owner: MemorySegment, userInfo: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRect:options:owner:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), options, owner, userInfo) as MemorySegment
    }
    
    // @property rect
    fun rect(): NSRect {
        val sel = ObjCRuntime.sel("rect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property options
    fun options(): NSTrackingAreaOptions {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTrackingAreaOptions
    }
    
    // @property owner
    fun owner(): MemorySegment {
        val sel = ObjCRuntime.sel("owner")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property userInfo
    /** @return NSDictionary<id,id> * */
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

