package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionView
 * Superclass: NSView
 * Protocols: NSDraggingSource, NSDraggingDestination
 */
open class NSCollectionView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionView") }
        
    }
    
    fun reloadData(): Unit {
        val sel = ObjCRuntime.sel("reloadData")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun layoutAttributesForItemAtIndexPath(indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForItemAtIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
    }
    
    fun layoutAttributesForSupplementaryElementOfKind_atIndexPath(kind: NSCollectionViewSupplementaryElementKind, indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("layoutAttributesForSupplementaryElementOfKind:atIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, kind, indexPath) as MemorySegment
    }
    
    fun frameForItemAtIndex(index: NSUInteger): NSRect {
        val sel = ObjCRuntime.sel("frameForItemAtIndex:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index) as NSRect
    }
    
    fun frameForItemAtIndex_withNumberOfItems(index: NSUInteger, numberOfItems: NSUInteger): NSRect {
        val sel = ObjCRuntime.sel("frameForItemAtIndex:withNumberOfItems:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, index, numberOfItems) as NSRect
    }
    
    fun numberOfItemsInSection(section: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItemsInSection:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, section) as NSInteger
    }
    
    fun selectItemsAtIndexPaths_scrollPosition(indexPaths: MemorySegment, scrollPosition: NSCollectionViewScrollPosition): Unit {
        val sel = ObjCRuntime.sel("selectItemsAtIndexPaths:scrollPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths, scrollPosition)
    }
    
    fun deselectItemsAtIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deselectItemsAtIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    fun selectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun deselectAll(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deselectAll:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun registerClass_forItemWithIdentifier(itemClass: Class<*>, identifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerClass:forItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, itemClass, identifier)
    }
    
    fun registerNib_forItemWithIdentifier(nib: MemorySegment, identifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerNib:forItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, nib, identifier)
    }
    
    fun registerClass_forSupplementaryViewOfKind_withIdentifier(viewClass: Class<*>, kind: NSCollectionViewSupplementaryElementKind, identifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerClass:forSupplementaryViewOfKind:withIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, viewClass, kind, identifier)
    }
    
    fun registerNib_forSupplementaryViewOfKind_withIdentifier(nib: MemorySegment, kind: NSCollectionViewSupplementaryElementKind, identifier: NSUserInterfaceItemIdentifier): Unit {
        val sel = ObjCRuntime.sel("registerNib:forSupplementaryViewOfKind:withIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, nib, kind, identifier)
    }
    
    fun makeItemWithIdentifier_forIndexPath(identifier: NSUserInterfaceItemIdentifier, indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeItemWithIdentifier:forIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier, indexPath) as MemorySegment
    }
    
    fun makeSupplementaryViewOfKind_withIdentifier_forIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, identifier: NSUserInterfaceItemIdentifier, indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeSupplementaryViewOfKind:withIdentifier:forIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, identifier, indexPath) as MemorySegment
    }
    
    fun itemAtIndex(index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun itemAtIndexPath(indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("itemAtIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
    }
    
    /** @return NSArray<NSCollectionViewItem *> * */
    fun visibleItems(): MemorySegment {
        val sel = ObjCRuntime.sel("visibleItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSSet<NSIndexPath *> * */
    fun indexPathsForVisibleItems(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathsForVisibleItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun indexPathForItem(item: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathForItem:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, item) as MemorySegment
    }
    
    fun indexPathForItemAtPoint(point: NSPoint): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathForItemAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
    }
    
    /** @return NSView<NSCollectionViewElement> * */
    fun supplementaryViewForElementKind_atIndexPath(elementKind: NSCollectionViewSupplementaryElementKind, indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("supplementaryViewForElementKind:atIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind, indexPath) as MemorySegment
    }
    
    /** @return NSArray<NSView<NSCollectionViewElement> *> * */
    fun visibleSupplementaryViewsOfKind(elementKind: NSCollectionViewSupplementaryElementKind): MemorySegment {
        val sel = ObjCRuntime.sel("visibleSupplementaryViewsOfKind:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
    }
    
    /** @return NSSet<NSIndexPath *> * */
    fun indexPathsForVisibleSupplementaryElementsOfKind(elementKind: NSCollectionViewSupplementaryElementKind): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathsForVisibleSupplementaryElementsOfKind:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, elementKind) as MemorySegment
    }
    
    fun insertSections(sections: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertSections:")
        ObjCRuntime.msgSend(null, ptr, sel, sections)
    }
    
    fun deleteSections(sections: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deleteSections:")
        ObjCRuntime.msgSend(null, ptr, sel, sections)
    }
    
    fun reloadSections(sections: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadSections:")
        ObjCRuntime.msgSend(null, ptr, sel, sections)
    }
    
    fun moveSection_toSection(section: NSInteger, newSection: NSInteger): Unit {
        val sel = ObjCRuntime.sel("moveSection:toSection:")
        ObjCRuntime.msgSend(null, ptr, sel, section, newSection)
    }
    
    fun insertItemsAtIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertItemsAtIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    fun deleteItemsAtIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deleteItemsAtIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    fun reloadItemsAtIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadItemsAtIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    fun moveItemAtIndexPath_toIndexPath(indexPath: MemorySegment, newIndexPath: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveItemAtIndexPath:toIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPath, newIndexPath)
    }
    
    fun performBatchUpdates_completionHandler(updates: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performBatchUpdates:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, updates, completionHandler)
    }
    
    fun toggleSectionCollapse(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleSectionCollapse:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun scrollToItemsAtIndexPaths_scrollPosition(indexPaths: MemorySegment, scrollPosition: NSCollectionViewScrollPosition): Unit {
        val sel = ObjCRuntime.sel("scrollToItemsAtIndexPaths:scrollPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths, scrollPosition)
    }
    
    fun setDraggingSourceOperationMask_forLocal(dragOperationMask: NSDragOperation, localDestination: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDraggingSourceOperationMask:forLocal:")
        ObjCRuntime.msgSend(null, ptr, sel, dragOperationMask, localDestination)
    }
    
    fun draggingImageForItemsAtIndexPaths_withEvent_offset(indexPaths: MemorySegment, event: MemorySegment, dragImageOffset: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageForItemsAtIndexPaths:withEvent:offset:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPaths, event, dragImageOffset) as MemorySegment
    }
    
    fun draggingImageForItemsAtIndexes_withEvent_offset(indexes: MemorySegment, event: MemorySegment, dragImageOffset: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageForItemsAtIndexes:withEvent:offset:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexes, event, dragImageOffset) as MemorySegment
    }
    
    // @property dataSource
    /** @return id<NSCollectionViewDataSource> */
    fun dataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("dataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDataSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prefetchDataSource
    /** @return id<NSCollectionViewPrefetching> */
    fun prefetchDataSource(): MemorySegment {
        val sel = ObjCRuntime.sel("prefetchDataSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrefetchDataSource(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrefetchDataSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property content
    /** @return NSArray<id> * */
    fun content(): MemorySegment {
        val sel = ObjCRuntime.sel("content")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSCollectionViewDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundView
    fun backgroundView(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundViewScrollsWithContent
    fun backgroundViewScrollsWithContent(): BOOL {
        val sel = ObjCRuntime.sel("backgroundViewScrollsWithContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBackgroundViewScrollsWithContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setBackgroundViewScrollsWithContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collectionViewLayout
    fun collectionViewLayout(): MemorySegment {
        val sel = ObjCRuntime.sel("collectionViewLayout")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCollectionViewLayout(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCollectionViewLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColors
    /** @return NSArray<NSColor *> * */
    fun backgroundColors(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberOfSections
    fun numberOfSections(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfSections")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property firstResponder
    fun isFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("isFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property selectable
    fun isSelectable(): BOOL {
        val sel = ObjCRuntime.sel("isSelectable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectable(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsEmptySelection
    fun allowsEmptySelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsEmptySelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsEmptySelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsEmptySelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsMultipleSelection
    fun allowsMultipleSelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsMultipleSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsMultipleSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsMultipleSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionIndexes
    fun selectionIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionIndexes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectionIndexPaths
    /** @return NSSet<NSIndexPath *> * */
    fun selectionIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelectionIndexPaths(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelectionIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSCollectionView ─────────────────────────────────────────

fun NSCollectionView.newItemForRepresentedObject(`object`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("newItemForRepresentedObject:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
}

fun NSCollectionView.itemPrototype(): MemorySegment {
    val sel = ObjCRuntime.sel("itemPrototype")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSCollectionView.setItemPrototype(itemPrototype: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setItemPrototype:")
    ObjCRuntime.msgSend(null, ptr, sel, itemPrototype)
}

fun NSCollectionView.maxNumberOfRows(): NSUInteger {
    val sel = ObjCRuntime.sel("maxNumberOfRows")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSCollectionView.setMaxNumberOfRows(maxNumberOfRows: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setMaxNumberOfRows:")
    ObjCRuntime.msgSend(null, ptr, sel, maxNumberOfRows)
}

fun NSCollectionView.maxNumberOfColumns(): NSUInteger {
    val sel = ObjCRuntime.sel("maxNumberOfColumns")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSCollectionView.setMaxNumberOfColumns(maxNumberOfColumns: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setMaxNumberOfColumns:")
    ObjCRuntime.msgSend(null, ptr, sel, maxNumberOfColumns)
}

fun NSCollectionView.minItemSize(): NSSize {
    val sel = ObjCRuntime.sel("minItemSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

fun NSCollectionView.setMinItemSize(minItemSize: NSSize): Unit {
    val sel = ObjCRuntime.sel("setMinItemSize:")
    ObjCRuntime.msgSend(null, ptr, sel, minItemSize)
}

fun NSCollectionView.maxItemSize(): NSSize {
    val sel = ObjCRuntime.sel("maxItemSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

fun NSCollectionView.setMaxItemSize(maxItemSize: NSSize): Unit {
    val sel = ObjCRuntime.sel("setMaxItemSize:")
    ObjCRuntime.msgSend(null, ptr, sel, maxItemSize)
}

// @property itemPrototype
    val sel = ObjCRuntime.sel("itemPrototype")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setItemPrototype:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property maxNumberOfRows
    val sel = ObjCRuntime.sel("maxNumberOfRows")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}
    val sel = ObjCRuntime.sel("setMaxNumberOfRows:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property maxNumberOfColumns
    val sel = ObjCRuntime.sel("maxNumberOfColumns")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}
    val sel = ObjCRuntime.sel("setMaxNumberOfColumns:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property minItemSize
    val sel = ObjCRuntime.sel("minItemSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}
    val sel = ObjCRuntime.sel("setMinItemSize:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property maxItemSize
    val sel = ObjCRuntime.sel("maxItemSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}
    val sel = ObjCRuntime.sel("setMaxItemSize:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

