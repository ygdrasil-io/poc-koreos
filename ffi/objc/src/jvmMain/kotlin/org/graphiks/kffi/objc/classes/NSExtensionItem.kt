/**
 * Kotlin/JVM wrapper for Objective-C class: NSExtensionItem
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSExtensionItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSExtensionItem") }
        
    }
    
    // @property attributedTitle
    fun attributedTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attributedContentText
    fun attributedContentText(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedContentText")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedContentText(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedContentText:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property attachments
    /** @return NSArray<NSItemProvider *> * */
    fun attachments(): MemorySegment {
        val sel = ObjCRuntime.sel("attachments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttachments(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttachments:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userInfo
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUserInfo(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUserInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

