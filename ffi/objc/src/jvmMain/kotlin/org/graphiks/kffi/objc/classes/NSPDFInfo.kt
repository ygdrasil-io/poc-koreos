/**
 * Kotlin/JVM wrapper for Objective-C class: NSPDFInfo
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPDFInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPDFInfo") }
        
    }
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileExtensionHidden
    fun isFileExtensionHidden(): BOOL {
        val sel = ObjCRuntime.sel("isFileExtensionHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setFileExtensionHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setFileExtensionHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tagNames
    /** @return NSArray<NSString *> * */
    fun tagNames(): MemorySegment {
        val sel = ObjCRuntime.sel("tagNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTagNames(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTagNames:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property orientation
    fun orientation(): NSPaperOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPaperOrientation
    }
    fun setOrientation(value: NSPaperOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paperSize
    fun paperSize(): NSSize {
        val sel = ObjCRuntime.sel("paperSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setPaperSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setPaperSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property attributes
    /** @return NSMutableDictionary<NSPrintInfoAttributeKey,id> * */
    fun attributes(): MemorySegment {
        val sel = ObjCRuntime.sel("attributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

