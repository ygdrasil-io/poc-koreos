/**
 * Kotlin/JVM wrapper for Objective-C class: NSBindingSelectionMarker
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSBindingSelectionMarker(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBindingSelectionMarker") }
        
        fun setDefaultPlaceholder_forMarker_onClass_withBinding(placeholder: MemorySegment, marker: MemorySegment, objectClass: Class, binding: NSBindingName): Unit {
            val sel = ObjCRuntime.sel("setDefaultPlaceholder:forMarker:onClass:withBinding:")
            ObjCRuntime.msgSend(null, _class, sel, placeholder, marker, objectClass, binding)
        }
        
        fun defaultPlaceholderForMarker_onClass_withBinding(marker: MemorySegment, objectClass: Class, binding: NSBindingName): MemorySegment {
            val sel = ObjCRuntime.sel("defaultPlaceholderForMarker:onClass:withBinding:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, marker, objectClass, binding) as MemorySegment
        }
        
        fun multipleValuesSelectionMarker(): MemorySegment {
            val sel = ObjCRuntime.sel("multipleValuesSelectionMarker")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun noSelectionMarker(): MemorySegment {
            val sel = ObjCRuntime.sel("noSelectionMarker")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun notApplicableSelectionMarker(): MemorySegment {
            val sel = ObjCRuntime.sel("notApplicableSelectionMarker")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property multipleValuesSelectionMarker
    fun multipleValuesSelectionMarker(): MemorySegment {
        val sel = ObjCRuntime.sel("multipleValuesSelectionMarker")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property noSelectionMarker
    fun noSelectionMarker(): MemorySegment {
        val sel = ObjCRuntime.sel("noSelectionMarker")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property notApplicableSelectionMarker
    fun notApplicableSelectionMarker(): MemorySegment {
        val sel = ObjCRuntime.sel("notApplicableSelectionMarker")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

