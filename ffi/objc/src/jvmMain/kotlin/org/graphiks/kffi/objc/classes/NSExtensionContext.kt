/**
 * Kotlin/JVM wrapper for Objective-C class: NSExtensionContext
 * Superclass: NSObject
 */
open class NSExtensionContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSExtensionContext") }
        
    }
    
    fun completeRequestReturningItems_completionHandler(items: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("completeRequestReturningItems:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, items, completionHandler)
    }
    
    fun cancelRequestWithError(error: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cancelRequestWithError:")
        ObjCRuntime.msgSend(null, ptr, sel, error)
    }
    
    fun openURL_completionHandler(URL: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, URL, completionHandler)
    }
    
    // @property inputItems
    fun inputItems(): MemorySegment {
        val sel = ObjCRuntime.sel("inputItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

