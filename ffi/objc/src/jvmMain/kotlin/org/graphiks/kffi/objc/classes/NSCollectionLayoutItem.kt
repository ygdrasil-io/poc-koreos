/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutItem
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutItem") }
        
        fun itemWithLayoutSize(layoutSize: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("itemWithLayoutSize:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, layoutSize) as MemorySegment
        }
        
        fun itemWithLayoutSize_supplementaryItems(layoutSize: MemorySegment, supplementaryItems: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("itemWithLayoutSize:supplementaryItems:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, layoutSize, supplementaryItems) as MemorySegment
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
    
    // @property contentInsets
    fun contentInsets(): NSDirectionalEdgeInsets {
        val sel = ObjCRuntime.sel("contentInsets")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("leading"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("trailing")).withName("NSDirectionalEdgeInsets"), ptr, sel) as NSDirectionalEdgeInsets
    }
    fun setContentInsets(value: NSDirectionalEdgeInsets) {
        val sel = ObjCRuntime.sel("setContentInsets:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("leading"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("trailing")).withName("NSDirectionalEdgeInsets")))
    }
    
    // @property edgeSpacing
    fun edgeSpacing(): MemorySegment {
        val sel = ObjCRuntime.sel("edgeSpacing")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEdgeSpacing(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEdgeSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layoutSize
    fun layoutSize(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property supplementaryItems
    /** @return NSArray<NSCollectionLayoutSupplementaryItem *> * */
    fun supplementaryItems(): MemorySegment {
        val sel = ObjCRuntime.sel("supplementaryItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

