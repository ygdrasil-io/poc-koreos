/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableViewDiffableDataSource
 * Superclass: NSObject
 * Protocols: NSTableViewDataSource
 */
open class NSTableViewDiffableDataSource(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableViewDiffableDataSource") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithTableView_cellProvider(tableView: MemorySegment, cellProvider: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTableView:cellProvider:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tableView, cellProvider) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSDiffableDataSourceSnapshot<SectionIdentifierType,ItemIdentifierType> * */
    fun snapshot(): MemorySegment {
        val sel = ObjCRuntime.sel("snapshot")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun applySnapshot_animatingDifferences(snapshot: MemorySegment, animatingDifferences: BOOL): Unit {
        val sel = ObjCRuntime.sel("applySnapshot:animatingDifferences:")
        ObjCRuntime.msgSend(null, ptr, sel, snapshot, animatingDifferences)
    }
    
    fun applySnapshot_animatingDifferences_completion(snapshot: MemorySegment, animatingDifferences: BOOL, completion: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("applySnapshot:animatingDifferences:completion:")
        ObjCRuntime.msgSend(null, ptr, sel, snapshot, animatingDifferences, completion)
    }
    
    fun itemIdentifierForRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("itemIdentifierForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun rowForItemIdentifier(identifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("rowForItemIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, identifier) as NSInteger
    }
    
    fun sectionIdentifierForRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("sectionIdentifierForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun rowForSectionIdentifier(identifier: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("rowForSectionIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, identifier) as NSInteger
    }
    
    // @property rowViewProvider
    fun rowViewProvider(): MemorySegment {
        val sel = ObjCRuntime.sel("rowViewProvider")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRowViewProvider(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRowViewProvider:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sectionHeaderViewProvider
    fun sectionHeaderViewProvider(): MemorySegment {
        val sel = ObjCRuntime.sel("sectionHeaderViewProvider")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSectionHeaderViewProvider(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSectionHeaderViewProvider:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultRowAnimation
    fun defaultRowAnimation(): NSTableViewAnimationOptions {
        val sel = ObjCRuntime.sel("defaultRowAnimation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableViewAnimationOptions
    }
    fun setDefaultRowAnimation(value: NSTableViewAnimationOptions) {
        val sel = ObjCRuntime.sel("setDefaultRowAnimation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

