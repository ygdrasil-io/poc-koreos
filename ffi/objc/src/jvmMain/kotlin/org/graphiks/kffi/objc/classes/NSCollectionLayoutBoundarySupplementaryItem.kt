/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutBoundarySupplementaryItem
 * Superclass: NSCollectionLayoutSupplementaryItem
 * Protocols: NSCopying
 */
open class NSCollectionLayoutBoundarySupplementaryItem(ptr: MemorySegment) : NSCollectionLayoutSupplementaryItem(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutBoundarySupplementaryItem") }
        
        fun boundarySupplementaryItemWithLayoutSize_elementKind_alignment(layoutSize: MemorySegment, elementKind: MemorySegment, alignment: NSRectAlignment): MemorySegment {
            val sel = ObjCRuntime.sel("boundarySupplementaryItemWithLayoutSize:elementKind:alignment:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, layoutSize, elementKind, alignment) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun boundarySupplementaryItemWithLayoutSize_elementKind_alignment(layoutSize: MemorySegment, elementKind: String, alignment: NSRectAlignment): MemorySegment = boundarySupplementaryItemWithLayoutSize_elementKind_alignment(layoutSize, ObjCRuntime.newNSString(Arena.global(), elementKind), alignment)
        
        fun boundarySupplementaryItemWithLayoutSize_elementKind_alignment_absoluteOffset(layoutSize: MemorySegment, elementKind: MemorySegment, alignment: NSRectAlignment, absoluteOffset: NSPoint): MemorySegment {
            val sel = ObjCRuntime.sel("boundarySupplementaryItemWithLayoutSize:elementKind:alignment:absoluteOffset:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, layoutSize, elementKind, alignment, ObjCRuntime.ObjCStructArg(absoluteOffset, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun boundarySupplementaryItemWithLayoutSize_elementKind_alignment_absoluteOffset(layoutSize: MemorySegment, elementKind: String, alignment: NSRectAlignment, absoluteOffset: NSPoint): MemorySegment = boundarySupplementaryItemWithLayoutSize_elementKind_alignment_absoluteOffset(layoutSize, ObjCRuntime.newNSString(Arena.global(), elementKind), alignment, absoluteOffset)
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property extendsBoundary
    fun extendsBoundary(): BOOL {
        val sel = ObjCRuntime.sel("extendsBoundary")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setExtendsBoundary(value: BOOL) {
        val sel = ObjCRuntime.sel("setExtendsBoundary:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pinToVisibleBounds
    fun pinToVisibleBounds(): BOOL {
        val sel = ObjCRuntime.sel("pinToVisibleBounds")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPinToVisibleBounds(value: BOOL) {
        val sel = ObjCRuntime.sel("setPinToVisibleBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alignment
    fun alignment(): NSRectAlignment {
        val sel = ObjCRuntime.sel("alignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRectAlignment
    }
    
    // @property offset
    fun offset(): NSPoint {
        val sel = ObjCRuntime.sel("offset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
}

