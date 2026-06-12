package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewFlowLayout
 * Superclass: NSCollectionViewLayout
 */
open class NSCollectionViewFlowLayout(ptr: MemorySegment) : NSCollectionViewLayout(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewFlowLayout") }
        
    }
    
    fun sectionAtIndexIsCollapsed(sectionIndex: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("sectionAtIndexIsCollapsed:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, sectionIndex) as BOOL
    }
    
    fun collapseSectionAtIndex(sectionIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("collapseSectionAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIndex)
    }
    
    fun expandSectionAtIndex(sectionIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("expandSectionAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIndex)
    }
    
    // @property minimumLineSpacing
    fun minimumLineSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("minimumLineSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumLineSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumLineSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumInteritemSpacing
    fun minimumInteritemSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("minimumInteritemSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumInteritemSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumInteritemSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property itemSize
    fun itemSize(): NSSize {
        val sel = ObjCRuntime.sel("itemSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setItemSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setItemSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property estimatedItemSize
    fun estimatedItemSize(): NSSize {
        val sel = ObjCRuntime.sel("estimatedItemSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setEstimatedItemSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setEstimatedItemSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property scrollDirection
    fun scrollDirection(): NSCollectionViewScrollDirection {
        val sel = ObjCRuntime.sel("scrollDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionViewScrollDirection
    }
    fun setScrollDirection(value: NSCollectionViewScrollDirection) {
        val sel = ObjCRuntime.sel("setScrollDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property headerReferenceSize
    fun headerReferenceSize(): NSSize {
        val sel = ObjCRuntime.sel("headerReferenceSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setHeaderReferenceSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setHeaderReferenceSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property footerReferenceSize
    fun footerReferenceSize(): NSSize {
        val sel = ObjCRuntime.sel("footerReferenceSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setFooterReferenceSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setFooterReferenceSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property sectionInset
    fun sectionInset(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("sectionInset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setSectionInset(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setSectionInset:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
    // @property sectionHeadersPinToVisibleBounds
    fun sectionHeadersPinToVisibleBounds(): BOOL {
        val sel = ObjCRuntime.sel("sectionHeadersPinToVisibleBounds")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSectionHeadersPinToVisibleBounds(value: BOOL) {
        val sel = ObjCRuntime.sel("setSectionHeadersPinToVisibleBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sectionFootersPinToVisibleBounds
    fun sectionFootersPinToVisibleBounds(): BOOL {
        val sel = ObjCRuntime.sel("sectionFootersPinToVisibleBounds")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSectionFootersPinToVisibleBounds(value: BOOL) {
        val sel = ObjCRuntime.sel("setSectionFootersPinToVisibleBounds:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

