/**
 * Kotlin/JVM wrapper for Objective-C class: NSBundleResourceRequest
 * Superclass: NSObject
 * Protocols: NSProgressReporting
 */
open class NSBundleResourceRequest(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBundleResourceRequest") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithTags(tags: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTags:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tags) as MemorySegment
    }
    
    fun initWithTags_bundle(tags: MemorySegment, bundle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTags:bundle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tags, bundle) as MemorySegment
    }
    
    fun beginAccessingResourcesWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginAccessingResourcesWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun conditionallyBeginAccessingResourcesWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("conditionallyBeginAccessingResourcesWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    fun endAccessingResources(): Unit {
        val sel = ObjCRuntime.sel("endAccessingResources")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property loadingPriority
    fun loadingPriority(): Double {
        val sel = ObjCRuntime.sel("loadingPriority")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setLoadingPriority(value: Double) {
        val sel = ObjCRuntime.sel("setLoadingPriority:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tags
    /** @return NSSet<NSString *> * */
    fun tags(): MemorySegment {
        val sel = ObjCRuntime.sel("tags")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bundle
    fun bundle(): MemorySegment {
        val sel = ObjCRuntime.sel("bundle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property progress
    fun progress(): MemorySegment {
        val sel = ObjCRuntime.sel("progress")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

