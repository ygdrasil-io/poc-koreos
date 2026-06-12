package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScrubberLayout
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScrubberLayout(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScrubberLayout") }
        
        open fun layoutAttributesClass(): Class<*> {
            val sel = ObjCRuntime.sel("layoutAttributesClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class<*>
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun invalidateLayout(): Unit {
        val sel = ObjCRuntime.sel("invalidateLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun prepareLayout(): Unit {
        val sel = ObjCRuntime.sel("prepareLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun layoutAttributesForItemAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForItemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    /** @return NSSet<__kindof NSScrubberLayoutAttributes *> * */
    open fun layoutAttributesForItemsInRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForItemsInRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    open fun shouldInvalidateLayoutForChangeFromVisibleRect_toVisibleRect(fromVisibleRect: NSRect, toVisibleRect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForChangeFromVisibleRect:toVisibleRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(fromVisibleRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(toVisibleRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    // @property layoutAttributesClass
    }
    
    // @property scrubber
    open fun scrubber(): MemorySegment {
        val sel = ObjCRuntime.sel("scrubber")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property visibleRect
    open fun visibleRect(): NSRect {
        val sel = ObjCRuntime.sel("visibleRect")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property scrubberContentSize
    open fun scrubberContentSize(): NSSize {
        val sel = ObjCRuntime.sel("scrubberContentSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property shouldInvalidateLayoutForSelectionChange
    open fun shouldInvalidateLayoutForSelectionChange(): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForSelectionChange")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property shouldInvalidateLayoutForHighlightChange
    open fun shouldInvalidateLayoutForHighlightChange(): BOOL {
        val sel = ObjCRuntime.sel("shouldInvalidateLayoutForHighlightChange")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticallyMirrorsInRightToLeftLayout
    open fun automaticallyMirrorsInRightToLeftLayout(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyMirrorsInRightToLeftLayout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

