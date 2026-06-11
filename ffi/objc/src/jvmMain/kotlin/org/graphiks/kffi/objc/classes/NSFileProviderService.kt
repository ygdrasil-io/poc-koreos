/**
 * Kotlin/JVM wrapper for Objective-C class: NSFileProviderService
 * Superclass: NSObject
 */
open class NSFileProviderService(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFileProviderService") }
        
    }
    
    fun getFileProviderConnectionWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getFileProviderConnectionWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    // @property name
    fun name(): NSFileProviderServiceName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFileProviderServiceName
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _name: MemorySegment
    // ivar: _endpointCreatingProxy: MemorySegment
    // ivar: _requestFinishedGroup: MemorySegment
}

