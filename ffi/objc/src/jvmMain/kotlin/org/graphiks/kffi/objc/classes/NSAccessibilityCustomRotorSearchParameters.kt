/**
 * Kotlin/JVM wrapper for Objective-C class: NSAccessibilityCustomRotorSearchParameters
 * Superclass: NSObject
 */
open class NSAccessibilityCustomRotorSearchParameters(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAccessibilityCustomRotorSearchParameters") }
        
    }
    
    // @property currentItem
    fun currentItem(): MemorySegment {
        val sel = ObjCRuntime.sel("currentItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrentItem(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrentItem:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property searchDirection
    fun searchDirection(): NSAccessibilityCustomRotorSearchDirection {
        val sel = ObjCRuntime.sel("searchDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAccessibilityCustomRotorSearchDirection
    }
    fun setSearchDirection(value: NSAccessibilityCustomRotorSearchDirection) {
        val sel = ObjCRuntime.sel("setSearchDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property filterString
    fun filterString(): MemorySegment {
        val sel = ObjCRuntime.sel("filterString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFilterString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFilterString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun filterStringAsString(): String = ObjCRuntime.toJavaString(filterString())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setFilterString(value: String) = setFilterString(ObjCRuntime.newNSString(Arena.global(), value))
    
}

