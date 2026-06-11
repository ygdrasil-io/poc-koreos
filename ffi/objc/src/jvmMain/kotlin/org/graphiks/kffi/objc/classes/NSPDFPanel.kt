/**
 * Kotlin/JVM wrapper for Objective-C class: NSPDFPanel
 * Superclass: NSObject
 */
open class NSPDFPanel(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPDFPanel") }
        
        fun panel(): MemorySegment {
            val sel = ObjCRuntime.sel("panel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun beginSheetWithPDFInfo_modalForWindow_completionHandler(pdfInfo: MemorySegment, docWindow: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetWithPDFInfo:modalForWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, pdfInfo, docWindow, completionHandler)
    }
    
    // @property accessoryController
    fun accessoryController(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property options
    fun options(): NSPDFPanelOptions {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPDFPanelOptions
    }
    fun setOptions(value: NSPDFPanelOptions) {
        val sel = ObjCRuntime.sel("setOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultFileName
    fun defaultFileName(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultFileName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDefaultFileName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDefaultFileName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun defaultFileNameAsString(): String = ObjCRuntime.toJavaString(defaultFileName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDefaultFileName(value: String) = setDefaultFileName(ObjCRuntime.newNSString(Arena.global(), value))
    
}

