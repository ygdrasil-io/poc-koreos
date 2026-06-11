/**
 * Kotlin/JVM wrapper for Objective-C class: NSFontAssetRequest
 * Superclass: NSObject
 * Protocols: NSProgressReporting
 */
open class NSFontAssetRequest(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFontAssetRequest") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithFontDescriptors_options(fontDescriptors: MemorySegment, options: NSFontAssetRequestOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFontDescriptors:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontDescriptors, options) as MemorySegment
    }
    
    fun downloadFontAssetsWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("downloadFontAssetsWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    // @property downloadedFontDescriptors
    /** @return NSArray<NSFontDescriptor *> * */
    fun downloadedFontDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("downloadedFontDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property progress
    fun progress(): MemorySegment {
        val sel = ObjCRuntime.sel("progress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

