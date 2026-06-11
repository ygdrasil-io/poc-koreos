/**
 * Kotlin/JVM wrapper for Objective-C class: NSDiffableDataSourceSnapshot
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSDiffableDataSourceSnapshot(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDiffableDataSourceSnapshot") }
        
    }
    
    fun numberOfItemsInSection(sectionIdentifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItemsInSection:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, sectionIdentifier) as NSInteger
    }
    
    /** @return NSArray<ItemIdentifierType> * */
    fun itemIdentifiersInSectionWithIdentifier(sectionIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifiersInSectionWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sectionIdentifier) as MemorySegment
    }
    
    fun sectionIdentifierForSectionContainingItemIdentifier(itemIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("sectionIdentifierForSectionContainingItemIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, itemIdentifier) as MemorySegment
    }
    
    fun indexOfItemIdentifier(itemIdentifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfItemIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, itemIdentifier) as NSInteger
    }
    
    fun indexOfSectionIdentifier(sectionIdentifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("indexOfSectionIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, sectionIdentifier) as NSInteger
    }
    
    fun appendItemsWithIdentifiers(identifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendItemsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers)
    }
    
    fun appendItemsWithIdentifiers_intoSectionWithIdentifier(identifiers: MemorySegment, sectionIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendItemsWithIdentifiers:intoSectionWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers, sectionIdentifier)
    }
    
    fun insertItemsWithIdentifiers_beforeItemWithIdentifier(identifiers: MemorySegment, itemIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertItemsWithIdentifiers:beforeItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers, itemIdentifier)
    }
    
    fun insertItemsWithIdentifiers_afterItemWithIdentifier(identifiers: MemorySegment, itemIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertItemsWithIdentifiers:afterItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers, itemIdentifier)
    }
    
    fun deleteItemsWithIdentifiers(identifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deleteItemsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers)
    }
    
    fun deleteAllItems(): Unit {
        val sel = ObjCRuntime.sel("deleteAllItems")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun moveItemWithIdentifier_beforeItemWithIdentifier(fromIdentifier: MemorySegment, toIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveItemWithIdentifier:beforeItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, fromIdentifier, toIdentifier)
    }
    
    fun moveItemWithIdentifier_afterItemWithIdentifier(fromIdentifier: MemorySegment, toIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveItemWithIdentifier:afterItemWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, fromIdentifier, toIdentifier)
    }
    
    fun reloadItemsWithIdentifiers(identifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadItemsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, identifiers)
    }
    
    fun appendSectionsWithIdentifiers(sectionIdentifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("appendSectionsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIdentifiers)
    }
    
    fun insertSectionsWithIdentifiers_beforeSectionWithIdentifier(sectionIdentifiers: MemorySegment, toSectionIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertSectionsWithIdentifiers:beforeSectionWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIdentifiers, toSectionIdentifier)
    }
    
    fun insertSectionsWithIdentifiers_afterSectionWithIdentifier(sectionIdentifiers: MemorySegment, toSectionIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertSectionsWithIdentifiers:afterSectionWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIdentifiers, toSectionIdentifier)
    }
    
    fun deleteSectionsWithIdentifiers(sectionIdentifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deleteSectionsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIdentifiers)
    }
    
    fun moveSectionWithIdentifier_beforeSectionWithIdentifier(fromSectionIdentifier: MemorySegment, toSectionIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveSectionWithIdentifier:beforeSectionWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, fromSectionIdentifier, toSectionIdentifier)
    }
    
    fun moveSectionWithIdentifier_afterSectionWithIdentifier(fromSectionIdentifier: MemorySegment, toSectionIdentifier: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveSectionWithIdentifier:afterSectionWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, fromSectionIdentifier, toSectionIdentifier)
    }
    
    fun reloadSectionsWithIdentifiers(sectionIdentifiers: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reloadSectionsWithIdentifiers:")
        ObjCRuntime.msgSend(null, ptr, sel, sectionIdentifiers)
    }
    
    // @property numberOfItems
    fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfSections
    fun numberOfSections(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfSections")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property sectionIdentifiers
    /** @return NSArray<SectionIdentifierType> * */
    fun sectionIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("sectionIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property itemIdentifiers
    /** @return NSArray<ItemIdentifierType> * */
    fun itemIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

