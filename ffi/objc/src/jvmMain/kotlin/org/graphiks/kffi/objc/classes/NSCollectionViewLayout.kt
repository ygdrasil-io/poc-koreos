package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewLayout
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSCollectionViewLayout(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewLayout") }
        
    }
    
    open fun invalidateLayout(): Unit {
        val sel = ObjCRuntime.sel("invalidateLayout")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun invalidateLayoutWithContext(context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invalidateLayoutWithContext:")
        ObjCRuntime.msgSend(null, ptr, sel, context)
    }
    
    open fun registerClass_forDecorationViewOfKind(viewClass: Class<*>, elementKind: NSCollectionViewDecorationElementKind): Unit {
        val sel = ObjCRuntime.sel("registerClass:forDecorationViewOfKind:")
        ObjCRuntime.msgSend(null, ptr, sel, viewClass, elementKind)
    }
    
    open fun registerNib_forDecorationViewOfKind(nib: MemorySegment, elementKind: NSCollectionViewDecorationElementKind): Unit {
        val sel = ObjCRuntime.sel("registerNib:forDecorationViewOfKind:")
        ObjCRuntime.msgSend(null, ptr, sel, nib, elementKind)
    }
    
    // @property collectionView
    open fun collectionView(): MemorySegment {
        val sel = ObjCRuntime.sel("collectionView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSSubclassingHooks on NSCollectionViewLayout ─────────────────────────────────────────

fun NSCollectionViewLayout.prepareLayout(): Unit {
    val sel = ObjCRuntime.sel("prepareLayout")
    ObjCRuntime.msgSend(null, ptr, sel)
}

/** @return NSArray<__kindof NSCollectionViewLayoutAttributes *> * */
fun NSCollectionViewLayout.layoutAttributesForElementsInRect(rect: NSRect): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForElementsInRect:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rect) as MemorySegment
}

fun NSCollectionViewLayout.layoutAttributesForItemAtIndexPath(indexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForItemAtIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
}

fun NSCollectionViewLayout.layoutAttributesForSupplementaryViewOfKind_atIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, indexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForSupplementaryViewOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, indexPath) as MemorySegment
}

fun NSCollectionViewLayout.layoutAttributesForDecorationViewOfKind_atIndexPath(elementKind: NSCollectionViewDecorationElementKind, indexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForDecorationViewOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, indexPath) as MemorySegment
}

fun NSCollectionViewLayout.layoutAttributesForDropTargetAtPoint(pointInCollectionView: NSPoint): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForDropTargetAtPoint:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pointInCollectionView) as MemorySegment
}

fun NSCollectionViewLayout.layoutAttributesForInterItemGapBeforeIndexPath(indexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("layoutAttributesForInterItemGapBeforeIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
}

fun NSCollectionViewLayout.shouldInvalidateLayoutForBoundsChange(newBounds: NSRect): BOOL {
    val sel = ObjCRuntime.sel("shouldInvalidateLayoutForBoundsChange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, newBounds) as BOOL
}

fun NSCollectionViewLayout.invalidationContextForBoundsChange(newBounds: NSRect): MemorySegment {
    val sel = ObjCRuntime.sel("invalidationContextForBoundsChange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newBounds) as MemorySegment
}

fun NSCollectionViewLayout.shouldInvalidateLayoutForPreferredLayoutAttributes_withOriginalAttributes(preferredAttributes: MemorySegment, originalAttributes: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("shouldInvalidateLayoutForPreferredLayoutAttributes:withOriginalAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, preferredAttributes, originalAttributes) as BOOL
}

fun NSCollectionViewLayout.invalidationContextForPreferredLayoutAttributes_withOriginalAttributes(preferredAttributes: MemorySegment, originalAttributes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("invalidationContextForPreferredLayoutAttributes:withOriginalAttributes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, preferredAttributes, originalAttributes) as MemorySegment
}

fun NSCollectionViewLayout.targetContentOffsetForProposedContentOffset_withScrollingVelocity(proposedContentOffset: NSPoint, velocity: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("targetContentOffsetForProposedContentOffset:withScrollingVelocity:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, proposedContentOffset, velocity) as NSPoint
}

fun NSCollectionViewLayout.targetContentOffsetForProposedContentOffset(proposedContentOffset: NSPoint): NSPoint {
    val sel = ObjCRuntime.sel("targetContentOffsetForProposedContentOffset:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel, proposedContentOffset) as NSPoint
}

fun NSCollectionViewLayout.collectionViewContentSize(): NSSize {
    val sel = ObjCRuntime.sel("collectionViewContentSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// Class<*> method: +[NSCollectionViewLayout layoutAttributesClass]
fun NSCollectionViewLayout_layoutAttributesClass(): Class<*> {
    val sel = ObjCRuntime.sel("layoutAttributesClass")
    val cls = ObjCRuntime.getClass("NSCollectionViewLayout")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as Class<*>
}

// Class<*> method: +[NSCollectionViewLayout invalidationContextClass]
fun NSCollectionViewLayout_invalidationContextClass(): Class<*> {
    val sel = ObjCRuntime.sel("invalidationContextClass")
    val cls = ObjCRuntime.getClass("NSCollectionViewLayout")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as Class<*>
}

// @property layoutAttributesClass
fun NSCollectionViewLayout.layoutAttributesClass(): Class<*> {
    val sel = ObjCRuntime.sel("layoutAttributesClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
}

// @property invalidationContextClass
fun NSCollectionViewLayout.invalidationContextClass(): Class<*> {
    val sel = ObjCRuntime.sel("invalidationContextClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
}

// @property collectionViewContentSize
    val sel = ObjCRuntime.sel("collectionViewContentSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// ── Category: NSUpdateSupportHooks on NSCollectionViewLayout ─────────────────────────────────────────

fun NSCollectionViewLayout.prepareForCollectionViewUpdates(updateItems: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("prepareForCollectionViewUpdates:")
    ObjCRuntime.msgSend(null, ptr, sel, updateItems)
}

fun NSCollectionViewLayout.finalizeCollectionViewUpdates(): Unit {
    val sel = ObjCRuntime.sel("finalizeCollectionViewUpdates")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSCollectionViewLayout.prepareForAnimatedBoundsChange(oldBounds: NSRect): Unit {
    val sel = ObjCRuntime.sel("prepareForAnimatedBoundsChange:")
    ObjCRuntime.msgSend(null, ptr, sel, oldBounds)
}

fun NSCollectionViewLayout.finalizeAnimatedBoundsChange(): Unit {
    val sel = ObjCRuntime.sel("finalizeAnimatedBoundsChange")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSCollectionViewLayout.prepareForTransitionToLayout(newLayout: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("prepareForTransitionToLayout:")
    ObjCRuntime.msgSend(null, ptr, sel, newLayout)
}

fun NSCollectionViewLayout.prepareForTransitionFromLayout(oldLayout: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("prepareForTransitionFromLayout:")
    ObjCRuntime.msgSend(null, ptr, sel, oldLayout)
}

fun NSCollectionViewLayout.finalizeLayoutTransition(): Unit {
    val sel = ObjCRuntime.sel("finalizeLayoutTransition")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSCollectionViewLayout.initialLayoutAttributesForAppearingItemAtIndexPath(itemIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initialLayoutAttributesForAppearingItemAtIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemIndexPath) as MemorySegment
}

fun NSCollectionViewLayout.finalLayoutAttributesForDisappearingItemAtIndexPath(itemIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("finalLayoutAttributesForDisappearingItemAtIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemIndexPath) as MemorySegment
}

fun NSCollectionViewLayout.initialLayoutAttributesForAppearingSupplementaryElementOfKind_atIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, elementIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initialLayoutAttributesForAppearingSupplementaryElementOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, elementIndexPath) as MemorySegment
}

fun NSCollectionViewLayout.finalLayoutAttributesForDisappearingSupplementaryElementOfKind_atIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, elementIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("finalLayoutAttributesForDisappearingSupplementaryElementOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, elementIndexPath) as MemorySegment
}

fun NSCollectionViewLayout.initialLayoutAttributesForAppearingDecorationElementOfKind_atIndexPath(elementKind: NSCollectionViewDecorationElementKind, decorationIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initialLayoutAttributesForAppearingDecorationElementOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, decorationIndexPath) as MemorySegment
}

fun NSCollectionViewLayout.finalLayoutAttributesForDisappearingDecorationElementOfKind_atIndexPath(elementKind: NSCollectionViewDecorationElementKind, decorationIndexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("finalLayoutAttributesForDisappearingDecorationElementOfKind:atIndexPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, decorationIndexPath) as MemorySegment
}

/** @return NSSet<NSIndexPath *> * */
fun NSCollectionViewLayout.indexPathsToDeleteForSupplementaryViewOfKind(elementKind: NSCollectionViewSupplementaryElementKind): MemorySegment {
    val sel = ObjCRuntime.sel("indexPathsToDeleteForSupplementaryViewOfKind:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
}

/** @return NSSet<NSIndexPath *> * */
fun NSCollectionViewLayout.indexPathsToDeleteForDecorationViewOfKind(elementKind: NSCollectionViewDecorationElementKind): MemorySegment {
    val sel = ObjCRuntime.sel("indexPathsToDeleteForDecorationViewOfKind:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
}

/** @return NSSet<NSIndexPath *> * */
fun NSCollectionViewLayout.indexPathsToInsertForSupplementaryViewOfKind(elementKind: NSCollectionViewSupplementaryElementKind): MemorySegment {
    val sel = ObjCRuntime.sel("indexPathsToInsertForSupplementaryViewOfKind:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
}

/** @return NSSet<NSIndexPath *> * */
fun NSCollectionViewLayout.indexPathsToInsertForDecorationViewOfKind(elementKind: NSCollectionViewDecorationElementKind): MemorySegment {
    val sel = ObjCRuntime.sel("indexPathsToInsertForDecorationViewOfKind:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
}

