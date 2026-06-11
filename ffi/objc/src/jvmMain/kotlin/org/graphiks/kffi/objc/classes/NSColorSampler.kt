/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorSampler
 * Superclass: NSObject
 */
open class NSColorSampler(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorSampler") }
        
    }
    
    fun showSamplerWithSelectionHandler(selectionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showSamplerWithSelectionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, selectionHandler)
    }
    
}

