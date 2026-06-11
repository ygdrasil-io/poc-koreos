/**
 * Kotlin/JVM wrapper for Objective-C class: NSViewLayoutRegion
 * Superclass: NSObject
 */
open class NSViewLayoutRegion(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSViewLayoutRegion") }
        
        fun safeAreaLayoutRegionWithCornerAdaptation(adaptivityAxis: NSViewLayoutRegionAdaptivityAxis): MemorySegment {
            val sel = ObjCRuntime.sel("safeAreaLayoutRegionWithCornerAdaptation:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, adaptivityAxis) as MemorySegment
        }
        
        fun marginsLayoutRegionWithCornerAdaptation(adaptivityAxis: NSViewLayoutRegionAdaptivityAxis): MemorySegment {
            val sel = ObjCRuntime.sel("marginsLayoutRegionWithCornerAdaptation:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, adaptivityAxis) as MemorySegment
        }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

