/**
 * Kotlin/JVM wrapper for Objective-C class: NSItemBadge
 * Superclass: NSObject
 */
open class NSItemBadge(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSItemBadge") }
        
        fun badgeWithCount(count: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("badgeWithCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, count) as MemorySegment
        }
        
        fun badgeWithText(text: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("badgeWithText:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, text) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun badgeWithText(text: String): MemorySegment = badgeWithText(ObjCRuntime.newNSString(Arena.global(), text))
        
        fun indicatorBadge(): MemorySegment {
            val sel = ObjCRuntime.sel("indicatorBadge")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property text
    fun text(): MemorySegment {
        val sel = ObjCRuntime.sel("text")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun textAsString(): String = ObjCRuntime.toJavaString(text())
    
}

