/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberLayout
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScrubberLayout(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberLayout") }
        
        fun layoutAttributesClass(): Class {
            val sel = ObjCRuntime.sel("layoutAttributesClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun invalidateLayout(): Unit {
        val sel = ObjCRuntime.sel("invalidateLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun prepareLayout(): Unit {
        val sel = ObjCRuntime.sel("prepareLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun layoutAttributesForItemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForItemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    /** @return NSSet<__kindof NSScrubberLayoutAttributes *> * */
    fun layoutAttributesForItemsInRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForItemsInRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun shouldInvalidateLayoutForChangeFromVisibleRect_toVisibleRect(fromVisibleRect: NSRect, toVisibleRect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForChangeFromVisibleRect:toVisibleRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(fromVisibleRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(toVisibleRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    // @property layoutAttributesClass
    fun layoutAttributesClass(): Class {
        val sel = ObjCRuntime.sel("layoutAttributesClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
    }
    
    // @property scrubber
    fun scrubber(): MemorySegment {
        val sel = ObjCRuntime.sel("scrubber")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property visibleRect
    fun visibleRect(): NSRect {
        val sel = ObjCRuntime.sel("visibleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property scrubberContentSize
    fun scrubberContentSize(): NSSize {
        val sel = ObjCRuntime.sel("scrubberContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property shouldInvalidateLayoutForSelectionChange
    fun shouldInvalidateLayoutForSelectionChange(): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForSelectionChange")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property shouldInvalidateLayoutForHighlightChange
    fun shouldInvalidateLayoutForHighlightChange(): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForHighlightChange")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticallyMirrorsInRightToLeftLayout
    fun automaticallyMirrorsInRightToLeftLayout(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyMirrorsInRightToLeftLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

