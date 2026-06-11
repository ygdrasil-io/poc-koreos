/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutSpacing
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutSpacing(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutSpacing") }
        
        fun flexibleSpacing(flexibleSpacing: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("flexibleSpacing:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, flexibleSpacing) as MemorySegment
        }
        
        fun fixedSpacing(fixedSpacing: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fixedSpacing:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fixedSpacing) as MemorySegment
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
    
    // @property spacing
    fun spacing(): CGFloat {
        val sel = ObjCRuntime.sel("spacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property isFlexibleSpacing
    fun isFlexibleSpacing(): BOOL {
        val sel = ObjCRuntime.sel("isFlexibleSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isFixedSpacing
    fun isFixedSpacing(): BOOL {
        val sel = ObjCRuntime.sel("isFixedSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

