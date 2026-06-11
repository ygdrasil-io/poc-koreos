/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewLayoutAttributes
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionViewLayoutAttributes(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewLayoutAttributes") }
        
        fun layoutAttributesForItemWithIndexPath(indexPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAttributesForItemWithIndexPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, indexPath) as MemorySegment
        }
        
        fun layoutAttributesForInterItemGapBeforeIndexPath(indexPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAttributesForInterItemGapBeforeIndexPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, indexPath) as MemorySegment
        }
        
        fun layoutAttributesForSupplementaryViewOfKind_withIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, indexPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAttributesForSupplementaryViewOfKind:withIndexPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, elementKind, indexPath) as MemorySegment
        }
        
        fun layoutAttributesForDecorationViewOfKind_withIndexPath(decorationViewKind: NSCollectionViewDecorationElementKind, indexPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAttributesForDecorationViewOfKind:withIndexPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, decorationViewKind, indexPath) as MemorySegment
        }
        
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property size
    fun size(): NSSize {
        val sel = ObjCRuntime.sel("size")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property alpha
    fun alpha(): CGFloat {
        val sel = ObjCRuntime.sel("alpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setAlpha(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property zIndex
    fun zIndex(): NSInteger {
        val sel = ObjCRuntime.sel("zIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setZIndex(value: NSInteger) {
        val sel = ObjCRuntime.sel("setZIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidden
    fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indexPath
    fun indexPath(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setIndexPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property representedElementCategory
    fun representedElementCategory(): NSCollectionElementCategory {
        val sel = ObjCRuntime.sel("representedElementCategory")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionElementCategory
    }
    
    // @property representedElementKind
    fun representedElementKind(): MemorySegment {
        val sel = ObjCRuntime.sel("representedElementKind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun representedElementKindAsString(): String = ObjCRuntime.toJavaString(representedElementKind())
    
}

