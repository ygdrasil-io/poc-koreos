/**
 * Kotlin/JVM wrapper for Objective-C class: NSMutableFontCollection
 * Superclass: NSFontCollection
 */
open class NSMutableFontCollection(ptr: MemorySegment) : NSFontCollection(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMutableFontCollection") }
        
        fun fontCollectionWithDescriptors(queryDescriptors: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontCollectionWithDescriptors:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, queryDescriptors) as MemorySegment
        }
        
        fun fontCollectionWithLocale(locale: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontCollectionWithLocale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, locale) as MemorySegment
        }
        
        fun fontCollectionWithName(name: NSFontCollectionName): MemorySegment {
            val sel = ObjCRuntime.sel("fontCollectionWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        fun fontCollectionWithName_visibility(name: NSFontCollectionName, visibility: NSFontCollectionVisibility): MemorySegment {
            val sel = ObjCRuntime.sel("fontCollectionWithName:visibility:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, visibility) as MemorySegment
        }
        
        fun fontCollectionWithAllAvailableDescriptors(): MemorySegment {
            val sel = ObjCRuntime.sel("fontCollectionWithAllAvailableDescriptors")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun addQueryForDescriptors(descriptors: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addQueryForDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptors)
    }
    
    fun removeQueryForDescriptors(descriptors: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeQueryForDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptors)
    }
    
    // @property fontCollectionWithAllAvailableDescriptors
    fun fontCollectionWithAllAvailableDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("fontCollectionWithAllAvailableDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property queryDescriptors
    /** @return NSArray<NSFontDescriptor *> * */
    fun queryDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("queryDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setQueryDescriptors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setQueryDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property exclusionDescriptors
    /** @return NSArray<NSFontDescriptor *> * */
    fun exclusionDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("exclusionDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExclusionDescriptors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExclusionDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

