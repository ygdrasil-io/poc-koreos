/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableViewRowAction
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSTableViewRowAction(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableViewRowAction") }
        
        fun rowActionWithStyle_title_handler(style: NSTableViewRowActionStyle, title: MemorySegment, handler: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("rowActionWithStyle:title:handler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, style, title, handler) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun rowActionWithStyle_title_handler(style: NSTableViewRowActionStyle, title: String, handler: MemorySegment): MemorySegment = rowActionWithStyle_title_handler(style, ObjCRuntime.newNSString(Arena.global(), title), handler)
        
    }
    
    // @property style
    fun style(): NSTableViewRowActionStyle {
        val sel = ObjCRuntime.sel("style")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewRowActionStyle
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

