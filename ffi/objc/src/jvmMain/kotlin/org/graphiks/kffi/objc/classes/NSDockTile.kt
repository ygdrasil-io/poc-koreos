/**
 * Kotlin/JVM wrapper for Objective-C class: NSDockTile
 * Superclass: NSObject
 */
open class NSDockTile(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDockTile") }
        
    }
    
    fun display(): Unit {
        val sel = ObjCRuntime.sel("display")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property size
    fun size(): NSSize {
        val sel = ObjCRuntime.sel("size")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property contentView
    fun contentView(): MemorySegment {
        val sel = ObjCRuntime.sel("contentView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsApplicationBadge
    fun showsApplicationBadge(): BOOL {
        val sel = ObjCRuntime.sel("showsApplicationBadge")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsApplicationBadge(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsApplicationBadge:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property badgeLabel
    fun badgeLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("badgeLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBadgeLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBadgeLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun badgeLabelAsString(): String = ObjCRuntime.toJavaString(badgeLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setBadgeLabel(value: String) = setBadgeLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property owner
    fun owner(): MemorySegment {
        val sel = ObjCRuntime.sel("owner")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

