package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextSelection
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSTextSelection(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextSelection") }
        
    }
    
    open fun initWithRanges_affinity_granularity(textRanges: MemorySegment, affinity: NSTextSelectionAffinity, granularity: NSTextSelectionGranularity): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRanges:affinity:granularity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRanges, affinity, granularity) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun initWithRange_affinity_granularity(range: MemorySegment, affinity: NSTextSelectionAffinity, granularity: NSTextSelectionGranularity): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRange:affinity:granularity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range, affinity, granularity) as MemorySegment
    }
    
    open fun initWithLocation_affinity(location: MemorySegment, affinity: NSTextSelectionAffinity): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLocation:affinity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, location, affinity) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun textSelectionWithTextRanges(textRanges: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("textSelectionWithTextRanges:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textRanges) as MemorySegment
    }
    
    // @property textRanges
    /** @return NSArray<NSTextRange *> * */
    open fun textRanges(): MemorySegment {
        val sel = ObjCRuntime.sel("textRanges")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property granularity
    open fun granularity(): NSTextSelectionGranularity {
        val sel = ObjCRuntime.sel("granularity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextSelectionGranularity
    }
    
    // @property affinity
    open fun affinity(): NSTextSelectionAffinity {
        val sel = ObjCRuntime.sel("affinity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextSelectionAffinity
    }
    
    // @property transient
    open fun isTransient(): BOOL {
        val sel = ObjCRuntime.sel("isTransient")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property anchorPositionOffset
    open fun anchorPositionOffset(): CGFloat {
        val sel = ObjCRuntime.sel("anchorPositionOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setAnchorPositionOffset(value: CGFloat) {
        val sel = ObjCRuntime.sel("setAnchorPositionOffset:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property logical
    open fun isLogical(): BOOL {
        val sel = ObjCRuntime.sel("isLogical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setLogical(value: BOOL) {
        val sel = ObjCRuntime.sel("setLogical:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property secondarySelectionLocation
    /** @return id<NSTextLocation> */
    open fun secondarySelectionLocation(): MemorySegment {
        val sel = ObjCRuntime.sel("secondarySelectionLocation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSecondarySelectionLocation(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSecondarySelectionLocation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property typingAttributes
    /** @return NSDictionary<NSAttributedStringKey,id> * */
    open fun typingAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("typingAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTypingAttributes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTypingAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

